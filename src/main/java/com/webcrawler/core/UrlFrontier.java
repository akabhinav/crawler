package com.webcrawler.core;

import com.webcrawler.domain.CrawlRequest;
import com.webcrawler.metrics.CrawlerMetrics;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * URL Frontier - The heart of the web crawler.
 *
 * Key responsibilities:
 * 1. URL deduplication using Bloom filter (for trillion-scale)
 * 2. Politeness enforcement (per-domain queues and delays)
 * 3. Priority-based crawling
 * 4. URL scheduling and rate limiting
 *
 * Design for 5 trillion pages:
 * - Bloom filter with 0.01% false positive rate
 * - Per-domain queues to enforce politeness
 * - Memory-efficient data structures
 * - Pluggable persistence for URL state
 */
@Slf4j
@Component
public class UrlFrontier {

    // Bloom filter for URL deduplication (5T URLs, 0.01% FP rate)
    // In production, this would be distributed (Redis/Cassandra)
    private final BloomFilter<String> seenUrls;

    // Per-domain queues for politeness
    private final Map<String, PriorityBlockingQueue<CrawlRequest>> domainQueues;

    // Track last access time per domain
    private final Map<String, Instant> lastAccessTime;

    // Track domain-specific crawl delays
    private final Map<String, Duration> domainDelays;

    // Global priority queue for scheduling
    private final PriorityBlockingQueue<CrawlRequest> readyQueue;

    // Locks for thread-safe operations
    private final Map<String, ReentrantLock> domainLocks;

    // Metrics
    private final CrawlerMetrics metrics;

    // Configuration
    private final Duration defaultPolitenessDelay;
    private final int maxUrlsPerDomain;

    public UrlFrontier(CrawlerMetrics metrics) {
        this.metrics = metrics;

        // Initialize Bloom filter for 5 trillion URLs with 0.01% false positive
        // This would use ~30GB memory - in production, use distributed solution
        this.seenUrls = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                5_000_000_000L, // 5B URLs for demo (scale to 5T with distributed system)
                0.0001 // 0.01% false positive rate
        );

        this.domainQueues = new ConcurrentHashMap<>();
        this.lastAccessTime = new ConcurrentHashMap<>();
        this.domainDelays = new ConcurrentHashMap<>();
        this.readyQueue = new PriorityBlockingQueue<>(10000);
        this.domainLocks = new ConcurrentHashMap<>();

        this.defaultPolitenessDelay = Duration.ofMillis(500);
        this.maxUrlsPerDomain = 100000;

        log.info("URL Frontier initialized - ready for trillion-scale crawling");
    }

    /**
     * Adds a URL to the frontier
     * @return true if URL was added, false if duplicate
     */
    public boolean add(CrawlRequest request) {
        String urlString = request.getUrlString();

        // Check Bloom filter for duplicates
        synchronized (seenUrls) {
            if (seenUrls.mightContain(urlString)) {
                log.debug("URL already seen (Bloom filter): {}", urlString);
                metrics.incrementDuplicateUrls();
                return false;
            }
            seenUrls.put(urlString);
        }

        String domain = request.getDomain();
        if (domain == null) {
            log.warn("Invalid URL - no domain: {}", urlString);
            return false;
        }

        // Add to domain-specific queue
        PriorityBlockingQueue<CrawlRequest> domainQueue = domainQueues.computeIfAbsent(
                domain,
                k -> new PriorityBlockingQueue<>(1000)
        );

        // Enforce per-domain queue size limit
        if (domainQueue.size() >= maxUrlsPerDomain) {
            log.debug("Domain queue full for {}, dropping URL: {}", domain, urlString);
            metrics.incrementDroppedUrls();
            return false;
        }

        domainQueue.offer(request);
        metrics.incrementQueuedUrls();

        log.trace("Added URL to frontier: {} (depth: {}, priority: {})",
                urlString, request.depth(), request.priority());

        return true;
    }

    /**
     * Gets the next URL to crawl, respecting politeness policies
     */
    public Optional<CrawlRequest> next() {
        // First, check ready queue
        CrawlRequest ready = readyQueue.poll();
        if (ready != null && isReadyToCrawl(ready)) {
            markDomainAccessed(ready.getDomain());
            metrics.incrementDequeuedUrls();
            return Optional.of(ready);
        }

        // Move URLs from domain queues to ready queue
        promoteReadyUrls();

        // Try ready queue again
        ready = readyQueue.poll();
        if (ready != null && isReadyToCrawl(ready)) {
            markDomainAccessed(ready.getDomain());
            metrics.incrementDequeuedUrls();
            return Optional.of(ready);
        }

        return Optional.empty();
    }

    /**
     * Sets politeness delay for a specific domain (from robots.txt)
     */
    public void setDomainDelay(String domain, Duration delay) {
        domainDelays.put(domain, delay);
        log.debug("Set crawl delay for {}: {}ms", domain, delay.toMillis());
    }

    /**
     * Gets current frontier size across all domains
     */
    public long size() {
        return domainQueues.values().stream()
                .mapToLong(PriorityBlockingQueue::size)
                .sum() + readyQueue.size();
    }

    /**
     * Gets number of domains in the frontier
     */
    public int domainCount() {
        return domainQueues.size();
    }

    /**
     * Checks if frontier has URLs ready to crawl
     */
    public boolean hasNext() {
        return !readyQueue.isEmpty() || domainQueues.values().stream()
                .anyMatch(q -> !q.isEmpty());
    }

    /**
     * Checks if a domain is ready to be crawled (respects politeness)
     */
    private boolean isReadyToCrawl(CrawlRequest request) {
        String domain = request.getDomain();
        Instant lastAccess = lastAccessTime.get(domain);

        if (lastAccess == null) {
            return true;
        }

        Duration delay = domainDelays.getOrDefault(domain, defaultPolitenessDelay);
        Instant nextAllowedTime = lastAccess.plus(delay);

        return Instant.now().isAfter(nextAllowedTime);
    }

    /**
     * Marks a domain as accessed
     */
    private void markDomainAccessed(String domain) {
        lastAccessTime.put(domain, Instant.now());
    }

    /**
     * Promotes URLs from domain queues to ready queue
     */
    private void promoteReadyUrls() {
        for (Map.Entry<String, PriorityBlockingQueue<CrawlRequest>> entry : domainQueues.entrySet()) {
            String domain = entry.getKey();
            PriorityBlockingQueue<CrawlRequest> queue = entry.getValue();

            ReentrantLock lock = domainLocks.computeIfAbsent(domain, k -> new ReentrantLock());

            if (lock.tryLock()) {
                try {
                    CrawlRequest request = queue.peek();
                    if (request != null && isReadyToCrawl(request)) {
                        queue.poll();
                        readyQueue.offer(request);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Clears all URLs (for testing or reset)
     */
    public void clear() {
        domainQueues.clear();
        readyQueue.clear();
        lastAccessTime.clear();
        log.info("URL Frontier cleared");
    }

    /**
     * Gets statistics about the frontier
     */
    public FrontierStats getStats() {
        return new FrontierStats(
                size(),
                domainCount(),
                readyQueue.size(),
                (long) (seenUrls.expectedFpp() * 10000) // FP rate in basis points
        );
    }

    public record FrontierStats(
            long totalUrls,
            int domains,
            long readyUrls,
            long bloomFilterFalsePositiveRate
    ) {}
}

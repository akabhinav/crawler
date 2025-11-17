package com.webcrawler.core;

import com.webcrawler.domain.CrawlRequest;
import com.webcrawler.metrics.CrawlerMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based URL Frontier - Distributed, persistent URL queue.
 *
 * Features:
 * - Distributed URL queue using Redis Sorted Sets
 * - Automatic deduplication using Redis Sets
 * - Per-domain queues for politeness
 * - Priority-based scheduling
 * - Persistence across restarts
 * - Supports multiple crawler instances
 *
 * Perfect for trillion-scale crawling with horizontal scaling.
 *
 * Redis Data Structures:
 * - crawler:urls:seen (SET) - Bloom filter replacement for deduplication
 * - crawler:queue:{domain} (ZSET) - Per-domain priority queues (score = priority + timestamp)
 * - crawler:domains (SET) - Active domains
 * - crawler:last-access:{domain} (STRING) - Last access timestamp per domain
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "crawler.frontier.type", havingValue = "redis")
public class RedisUrlFrontier {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CrawlerMetrics metrics;

    private final Duration defaultPolitenessDelay;
    private final int maxUrlsPerDomain;

    private static final String SEEN_URLS_KEY = "crawler:urls:seen";
    private static final String DOMAINS_KEY = "crawler:domains";
    private static final String QUEUE_PREFIX = "crawler:queue:";
    private static final String LAST_ACCESS_PREFIX = "crawler:last-access:";
    private static final String DOMAIN_DELAY_PREFIX = "crawler:delay:";

    public RedisUrlFrontier(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CrawlerMetrics metrics,
            @Value("${crawler.politeness.delay:500}") long politenessDelayMs,
            @Value("${crawler.frontier.max-urls-per-domain:100000}") int maxUrlsPerDomain
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.defaultPolitenessDelay = Duration.ofMillis(politenessDelayMs);
        this.maxUrlsPerDomain = maxUrlsPerDomain;

        log.info("Redis URL Frontier initialized - politeness: {}ms, max per domain: {}",
                politenessDelayMs, maxUrlsPerDomain);
    }

    /**
     * Adds a URL to the frontier
     * @return true if URL was added, false if duplicate
     */
    public boolean add(CrawlRequest request) {
        String urlString = request.getUrlString();

        // Check if URL was already seen (deduplication)
        Boolean added = redisTemplate.opsForSet().add(SEEN_URLS_KEY, urlString);
        if (added == null || !added) {
            log.debug("URL already seen (Redis): {}", urlString);
            metrics.incrementDuplicateUrls();
            return false;
        }

        String domain = request.getDomain();
        if (domain == null) {
            log.warn("Invalid URL - no domain: {}", urlString);
            return false;
        }

        // Check domain queue size
        String queueKey = QUEUE_PREFIX + domain;
        Long queueSize = redisTemplate.opsForZSet().size(queueKey);
        if (queueSize != null && queueSize >= maxUrlsPerDomain) {
            log.debug("Domain queue full for {}, dropping URL: {}", domain, urlString);
            metrics.incrementDroppedUrls();
            return false;
        }

        try {
            // Serialize request to JSON
            String requestJson = objectMapper.writeValueAsString(request);

            // Add to per-domain queue (sorted by priority and scheduled time)
            double score = calculateScore(request);
            redisTemplate.opsForZSet().add(queueKey, requestJson, score);

            // Add domain to active domains set
            redisTemplate.opsForSet().add(DOMAINS_KEY, domain);

            metrics.incrementQueuedUrls();

            log.trace("Added URL to Redis frontier: {} (domain: {}, priority: {}, score: {})",
                    urlString, domain, request.priority(), score);

            return true;

        } catch (Exception e) {
            log.error("Error adding URL to Redis frontier: {}", urlString, e);
            return false;
        }
    }

    /**
     * Gets the next URL to crawl, respecting politeness policies
     */
    public Optional<CrawlRequest> next() {
        try {
            // Get all active domains
            Set<String> domains = redisTemplate.opsForSet().members(DOMAINS_KEY);
            if (domains == null || domains.isEmpty()) {
                return Optional.empty();
            }

            // Try each domain to find a ready URL
            for (String domain : domains) {
                if (isReadyToCrawl(domain)) {
                    Optional<CrawlRequest> request = pollFromDomain(domain);
                    if (request.isPresent()) {
                        markDomainAccessed(domain);
                        metrics.incrementDequeuedUrls();
                        return request;
                    }
                }
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Error getting next URL from Redis frontier", e);
            return Optional.empty();
        }
    }

    /**
     * Sets politeness delay for a specific domain (from robots.txt)
     */
    public void setDomainDelay(String domain, Duration delay) {
        String delayKey = DOMAIN_DELAY_PREFIX + domain;
        redisTemplate.opsForValue().set(delayKey, String.valueOf(delay.toMillis()));
        log.debug("Set crawl delay for {} in Redis: {}ms", domain, delay.toMillis());
    }

    /**
     * Gets current frontier size across all domains
     */
    public long size() {
        try {
            Set<String> domains = redisTemplate.opsForSet().members(DOMAINS_KEY);
            if (domains == null) {
                return 0;
            }

            long total = 0;
            for (String domain : domains) {
                Long size = redisTemplate.opsForZSet().size(QUEUE_PREFIX + domain);
                total += (size != null ? size : 0);
            }
            return total;

        } catch (Exception e) {
            log.error("Error getting frontier size from Redis", e);
            return 0;
        }
    }

    /**
     * Gets number of domains in the frontier
     */
    public int domainCount() {
        Long count = redisTemplate.opsForSet().size(DOMAINS_KEY);
        return count != null ? count.intValue() : 0;
    }

    /**
     * Checks if frontier has URLs ready to crawl
     */
    public boolean hasNext() {
        return size() > 0;
    }

    /**
     * Clears all URLs (for testing or reset)
     */
    public void clear() {
        try {
            Set<String> domains = redisTemplate.opsForSet().members(DOMAINS_KEY);
            if (domains != null) {
                for (String domain : domains) {
                    redisTemplate.delete(QUEUE_PREFIX + domain);
                    redisTemplate.delete(LAST_ACCESS_PREFIX + domain);
                    redisTemplate.delete(DOMAIN_DELAY_PREFIX + domain);
                }
            }

            redisTemplate.delete(SEEN_URLS_KEY);
            redisTemplate.delete(DOMAINS_KEY);

            log.info("Redis URL Frontier cleared");

        } catch (Exception e) {
            log.error("Error clearing Redis frontier", e);
        }
    }

    /**
     * Polls next request from a specific domain
     */
    private Optional<CrawlRequest> pollFromDomain(String domain) {
        try {
            String queueKey = QUEUE_PREFIX + domain;

            // Get highest priority item (lowest score in ZSET)
            Set<String> items = redisTemplate.opsForZSet().range(queueKey, 0, 0);
            if (items == null || items.isEmpty()) {
                // Remove empty domain
                redisTemplate.opsForSet().remove(DOMAINS_KEY, domain);
                return Optional.empty();
            }

            String requestJson = items.iterator().next();

            // Remove from queue
            redisTemplate.opsForZSet().remove(queueKey, requestJson);

            // Deserialize request
            CrawlRequest request = objectMapper.readValue(requestJson, CrawlRequest.class);

            return Optional.of(request);

        } catch (Exception e) {
            log.error("Error polling from domain {}", domain, e);
            return Optional.empty();
        }
    }

    /**
     * Checks if a domain is ready to be crawled (respects politeness)
     */
    private boolean isReadyToCrawl(String domain) {
        try {
            String lastAccessKey = LAST_ACCESS_PREFIX + domain;
            String lastAccessStr = redisTemplate.opsForValue().get(lastAccessKey);

            if (lastAccessStr == null) {
                return true;
            }

            Instant lastAccess = Instant.parse(lastAccessStr);
            Duration delay = getDomainDelay(domain);
            Instant nextAllowedTime = lastAccess.plus(delay);

            return Instant.now().isAfter(nextAllowedTime);

        } catch (Exception e) {
            log.error("Error checking if domain {} is ready", domain, e);
            return true; // Default to allowing
        }
    }

    /**
     * Marks a domain as accessed
     */
    private void markDomainAccessed(String domain) {
        String lastAccessKey = LAST_ACCESS_PREFIX + domain;
        redisTemplate.opsForValue().set(lastAccessKey, Instant.now().toString());
    }

    /**
     * Gets crawl delay for a domain
     */
    private Duration getDomainDelay(String domain) {
        String delayKey = DOMAIN_DELAY_PREFIX + domain;
        String delayStr = redisTemplate.opsForValue().get(delayKey);

        if (delayStr != null) {
            try {
                return Duration.ofMillis(Long.parseLong(delayStr));
            } catch (Exception e) {
                log.warn("Invalid delay value for {}: {}", domain, delayStr);
            }
        }

        return defaultPolitenessDelay;
    }

    /**
     * Calculates score for sorting in Redis ZSET
     * Lower score = higher priority
     */
    private double calculateScore(CrawlRequest request) {
        // Negate priority so higher priority = lower score
        double priorityScore = -request.priority() * 1_000_000;

        // Add scheduled time in seconds
        double timeScore = request.scheduledTime().getEpochSecond();

        return priorityScore + timeScore;
    }

    /**
     * Gets statistics about the frontier
     */
    public FrontierStats getStats() {
        return new FrontierStats(
                size(),
                domainCount(),
                0, // Ready URLs count (expensive to calculate in Redis)
                0  // N/A for Redis
        );
    }

    public record FrontierStats(
            long totalUrls,
            int domains,
            long readyUrls,
            long bloomFilterFalsePositiveRate
    ) {}
}

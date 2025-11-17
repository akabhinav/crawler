package com.webcrawler.core;

import com.webcrawler.domain.CrawlRequest;
import com.webcrawler.domain.CrawlResult;
import com.webcrawler.filter.UrlFilter;
import com.webcrawler.handler.CrawlHandler;
import com.webcrawler.metrics.CrawlerMetrics;
import com.webcrawler.service.FetcherService;
import com.webcrawler.service.ParserService;
import com.webcrawler.service.RobotsTxtService;
import com.webcrawler.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Crawler Orchestrator - The main coordinator of the crawling process.
 *
 * Architecture:
 * 1. Pulls URLs from UrlFrontier
 * 2. Checks robots.txt compliance
 * 3. Fetches the page
 * 4. Parses content and extracts links
 * 5. Stores results
 * 6. Adds discovered links back to frontier
 *
 * Features:
 * - Concurrent crawling using Virtual Threads
 * - Extensible pipeline with filters and handlers
 * - Graceful shutdown
 * - Backpressure handling
 */
@Slf4j
@Component
public class CrawlerOrchestrator {

    private final UrlFrontier urlFrontier;
    private final FetcherService fetcherService;
    private final ParserService parserService;
    private final RobotsTxtService robotsTxtService;
    private final StorageService storageService;
    private final CrawlerMetrics metrics;

    private final List<UrlFilter> urlFilters;
    private final List<CrawlHandler> crawlHandlers;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${crawler.max-depth:5}")
    private int maxDepth;

    @Value("${crawler.concurrency:100}")
    private int concurrency;

    @Value("${crawler.enable-robots-txt:true}")
    private boolean enableRobotsTxt;

    public CrawlerOrchestrator(
            UrlFrontier urlFrontier,
            FetcherService fetcherService,
            ParserService parserService,
            RobotsTxtService robotsTxtService,
            StorageService storageService,
            CrawlerMetrics metrics,
            List<UrlFilter> urlFilters,
            List<CrawlHandler> crawlHandlers
    ) {
        this.urlFrontier = urlFrontier;
        this.fetcherService = fetcherService;
        this.parserService = parserService;
        this.robotsTxtService = robotsTxtService;
        this.storageService = storageService;
        this.metrics = metrics;
        this.urlFilters = urlFilters != null ? urlFilters : List.of();
        this.crawlHandlers = crawlHandlers != null ? crawlHandlers : List.of();

        log.info("Crawler Orchestrator initialized - concurrency: {}, max-depth: {}",
                concurrency, maxDepth);
    }

    /**
     * Starts the crawler
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting crawler...");

            Flux.interval(Duration.ofMillis(100))
                    .takeWhile(i -> running.get())
                    .flatMap(i -> processBatch(), concurrency)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            result -> log.debug("Processed: {}", result.getUrl()),
                            error -> log.error("Crawler error: {}", error.getMessage()),
                            () -> log.info("Crawler stopped")
                    );

            log.info("Crawler started with {} concurrent workers", concurrency);
        } else {
            log.warn("Crawler is already running");
        }
    }

    /**
     * Stops the crawler gracefully
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping crawler...");
        }
    }

    /**
     * Adds a seed URL to start crawling
     */
    public void addSeed(String url) {
        CrawlRequest request = CrawlRequest.seed(url);
        urlFrontier.add(request);
        log.info("Added seed URL: {}", url);
    }

    /**
     * Processes a batch of URLs
     */
    private Mono<CrawlResult> processBatch() {
        return Mono.fromCallable(() -> urlFrontier.next())
                .flatMap(optRequest -> {
                    if (optRequest.isEmpty()) {
                        return Mono.empty();
                    }

                    CrawlRequest request = optRequest.get();
                    return crawl(request);
                });
    }

    /**
     * Crawls a single URL
     */
    private Mono<CrawlResult> crawl(CrawlRequest request) {
        // Check depth limit
        if (request.depth() > maxDepth) {
            log.debug("Skipping URL (max depth): {}", request.getUrlString());
            return Mono.empty();
        }

        // Check robots.txt
        if (enableRobotsTxt && !robotsTxtService.isAllowed(request.getUrlString())) {
            log.debug("Skipping URL (robots.txt): {}", request.getUrlString());
            return Mono.empty();
        }

        // Apply URL filters
        for (UrlFilter filter : urlFilters) {
            if (!filter.shouldCrawl(request)) {
                log.debug("Skipping URL (filter: {}): {}",
                        filter.getClass().getSimpleName(), request.getUrlString());
                return Mono.empty();
            }
        }

        log.debug("Crawling: {} (depth: {}, priority: {})",
                request.getUrlString(), request.depth(), request.priority());

        metrics.incrementActiveFetches();

        return fetcherService.fetch(request)
                .flatMap(result -> {
                    // Parse and extract links
                    CrawlResult parsed = parserService.parse(result);

                    // Store result
                    storageService.store(parsed);

                    // Process extracted links
                    if (parsed.extractedLinks() != null) {
                        processExtractedLinks(request, parsed.extractedLinks());
                        metrics.incrementLinksExtracted(parsed.extractedLinks().size());
                    }

                    // Execute custom handlers
                    for (CrawlHandler handler : crawlHandlers) {
                        handler.handle(parsed);
                    }

                    metrics.incrementPagesProcessed();
                    metrics.decrementActiveFetches();

                    return Mono.just(parsed);
                })
                .onErrorResume(error -> {
                    log.error("Error crawling {}: {}", request.getUrlString(), error.getMessage());
                    metrics.decrementActiveFetches();
                    return Mono.empty();
                });
    }

    /**
     * Processes extracted links and adds them to the frontier
     */
    private void processExtractedLinks(CrawlRequest parent, List<URI> links) {
        for (URI link : links) {
            // Apply filters
            boolean allowed = true;
            for (UrlFilter filter : urlFilters) {
                CrawlRequest childRequest = parent.createChild(link, -1);
                if (!filter.shouldCrawl(childRequest)) {
                    allowed = false;
                    break;
                }
            }

            if (allowed) {
                CrawlRequest childRequest = parent.createChild(link, -1);
                urlFrontier.add(childRequest);
            }
        }
    }

    /**
     * Checks if the crawler is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets frontier statistics
     */
    public UrlFrontier.FrontierStats getFrontierStats() {
        return urlFrontier.getStats();
    }

    /**
     * Gets crawler metrics
     */
    public CrawlerMetrics.CrawlerStats getCrawlerStats() {
        return metrics.getStats();
    }
}

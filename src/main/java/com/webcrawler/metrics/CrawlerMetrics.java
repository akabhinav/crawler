package com.webcrawler.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Crawler Metrics - Observability for the crawler.
 *
 * Tracks:
 * - URLs queued/dequeued/crawled
 * - Fetch success/failure rates
 * - Fetch duration
 * - Circuit breaker events
 * - Storage operations
 *
 * Metrics are exposed via Prometheus for monitoring dashboards.
 */
@Slf4j
@Component
public class CrawlerMetrics {

    private final Counter queuedUrls;
    private final Counter dequeuedUrls;
    private final Counter duplicateUrls;
    private final Counter droppedUrls;
    private final Counter fetchSuccess;
    private final Counter fetchFailure;
    private final Counter circuitBreakerOpen;
    private final Counter pagesProcessed;
    private final Counter linksExtracted;
    private final Timer fetchDuration;

    private final AtomicLong activeFetches;

    public CrawlerMetrics(MeterRegistry registry) {
        this.queuedUrls = Counter.builder("crawler.urls.queued")
                .description("Total URLs added to the frontier")
                .register(registry);

        this.dequeuedUrls = Counter.builder("crawler.urls.dequeued")
                .description("Total URLs dequeued for crawling")
                .register(registry);

        this.duplicateUrls = Counter.builder("crawler.urls.duplicate")
                .description("Total duplicate URLs filtered")
                .register(registry);

        this.droppedUrls = Counter.builder("crawler.urls.dropped")
                .description("Total URLs dropped (queue full, etc.)")
                .register(registry);

        this.fetchSuccess = Counter.builder("crawler.fetch.success")
                .description("Total successful fetches")
                .register(registry);

        this.fetchFailure = Counter.builder("crawler.fetch.failure")
                .description("Total failed fetches")
                .register(registry);

        this.circuitBreakerOpen = Counter.builder("crawler.circuit_breaker.open")
                .description("Circuit breaker open events")
                .register(registry);

        this.pagesProcessed = Counter.builder("crawler.pages.processed")
                .description("Total pages processed")
                .register(registry);

        this.linksExtracted = Counter.builder("crawler.links.extracted")
                .description("Total links extracted")
                .register(registry);

        this.fetchDuration = Timer.builder("crawler.fetch.duration")
                .description("Fetch duration in milliseconds")
                .register(registry);

        this.activeFetches = registry.gauge("crawler.fetch.active",
                new AtomicLong(0));

        log.info("Crawler metrics initialized");
    }

    public void incrementQueuedUrls() {
        queuedUrls.increment();
    }

    public void incrementDequeuedUrls() {
        dequeuedUrls.increment();
    }

    public void incrementDuplicateUrls() {
        duplicateUrls.increment();
    }

    public void incrementDroppedUrls() {
        droppedUrls.increment();
    }

    public void recordFetchSuccess() {
        fetchSuccess.increment();
    }

    public void recordFetchFailure() {
        fetchFailure.increment();
    }

    public void recordCircuitBreakerOpen() {
        circuitBreakerOpen.increment();
    }

    public void incrementPagesProcessed() {
        pagesProcessed.increment();
    }

    public void incrementLinksExtracted(int count) {
        linksExtracted.increment(count);
    }

    public void recordFetchDuration(long durationMs) {
        fetchDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void incrementActiveFetches() {
        activeFetches.incrementAndGet();
    }

    public void decrementActiveFetches() {
        activeFetches.decrementAndGet();
    }

    // Summary stats
    public CrawlerStats getStats() {
        return new CrawlerStats(
                (long) queuedUrls.count(),
                (long) dequeuedUrls.count(),
                (long) duplicateUrls.count(),
                (long) droppedUrls.count(),
                (long) fetchSuccess.count(),
                (long) fetchFailure.count(),
                (long) pagesProcessed.count(),
                (long) linksExtracted.count(),
                activeFetches.get()
        );
    }

    public record CrawlerStats(
            long queuedUrls,
            long dequeuedUrls,
            long duplicateUrls,
            long droppedUrls,
            long successfulFetches,
            long failedFetches,
            long pagesProcessed,
            long linksExtracted,
            long activeFetches
    ) {}
}

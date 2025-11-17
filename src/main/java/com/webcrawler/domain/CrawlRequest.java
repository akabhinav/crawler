package com.webcrawler.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of a crawl request.
 * Uses Java 21 record for concise, immutable data carrier.
 *
 * @param url The URL to crawl
 * @param depth Current crawl depth (0 = seed URL)
 * @param priority Priority for crawling (higher = more important)
 * @param parentUrl The URL that linked to this one
 * @param metadata Additional metadata for extensibility
 * @param scheduledTime When this URL should be crawled (for politeness)
 * @param retryCount Number of times this URL has been retried
 */
public record CrawlRequest(
        URI url,
        int depth,
        int priority,
        URI parentUrl,
        Map<String, Object> metadata,
        Instant scheduledTime,
        int retryCount
) implements Comparable<CrawlRequest> {

    public CrawlRequest {
        Objects.requireNonNull(url, "URL cannot be null");
        if (depth < 0) {
            throw new IllegalArgumentException("Depth cannot be negative");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("Retry count cannot be negative");
        }
        // Defensive copy for mutable map
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        scheduledTime = scheduledTime != null ? scheduledTime : Instant.now();
    }

    /**
     * Creates a seed URL (depth 0, high priority)
     */
    public static CrawlRequest seed(String url) {
        return new CrawlRequest(
                URI.create(url),
                0,
                100,
                null,
                Map.of(),
                Instant.now(),
                0
        );
    }

    /**
     * Creates a child request from this one
     */
    public CrawlRequest createChild(URI childUrl, int priorityAdjustment) {
        return new CrawlRequest(
                childUrl,
                this.depth + 1,
                Math.max(0, this.priority + priorityAdjustment),
                this.url,
                this.metadata,
                Instant.now(),
                0
        );
    }

    /**
     * Creates a retry request with incremented retry count
     */
    public CrawlRequest retry(Instant nextScheduledTime) {
        return new CrawlRequest(
                url,
                depth,
                priority,
                parentUrl,
                metadata,
                nextScheduledTime,
                retryCount + 1
        );
    }

    /**
     * Priority comparison for queue ordering
     * Higher priority first, then earlier scheduled time, then by URL
     */
    @Override
    public int compareTo(CrawlRequest other) {
        int priorityCompare = Integer.compare(other.priority, this.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        int timeCompare = this.scheduledTime.compareTo(other.scheduledTime);
        if (timeCompare != 0) {
            return timeCompare;
        }

        return this.url.toString().compareTo(other.url.toString());
    }

    public String getDomain() {
        return url.getHost();
    }

    public String getUrlString() {
        return url.toString();
    }
}

package com.webcrawler.filter;

import com.webcrawler.domain.CrawlRequest;

/**
 * URL Filter Interface - Extensibility point for filtering URLs.
 *
 * Implementations can filter URLs based on:
 * - Domain whitelist/blacklist
 * - URL patterns (regex)
 * - Content type
 * - Custom business logic
 *
 * Filters are executed before fetching, saving bandwidth and time.
 */
public interface UrlFilter {

    /**
     * Determines if a URL should be crawled
     * @param request The crawl request
     * @return true if URL should be crawled
     */
    boolean shouldCrawl(CrawlRequest request);

    /**
     * Priority of this filter (lower = higher priority)
     * Filters are executed in priority order
     */
    default int getPriority() {
        return 100;
    }
}

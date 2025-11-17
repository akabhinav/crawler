package com.webcrawler.handler;

import com.webcrawler.domain.CrawlResult;

/**
 * Crawl Handler Interface - Extensibility point for processing crawl results.
 *
 * Implementations can:
 * - Index content into search engines
 * - Extract structured data
 * - Perform sentiment analysis
 * - Generate thumbnails
 * - Custom business logic
 *
 * Handlers are executed after successful crawling.
 */
public interface CrawlHandler {

    /**
     * Handles a crawl result
     * @param result The crawl result
     */
    void handle(CrawlResult result);

    /**
     * Priority of this handler (lower = higher priority)
     * Handlers are executed in priority order
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Whether this handler should run asynchronously
     */
    default boolean isAsync() {
        return true;
    }
}

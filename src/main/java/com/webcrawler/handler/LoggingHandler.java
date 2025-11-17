package com.webcrawler.handler;

import com.webcrawler.domain.CrawlResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logging Handler - Logs crawl results for debugging.
 */
@Slf4j
@Component
public class LoggingHandler implements CrawlHandler {

    @Override
    public void handle(CrawlResult result) {
        if (result.success()) {
            log.info("Crawled: {} - Status: {} - Links: {} - Duration: {}ms",
                    result.getUrl(),
                    result.statusCode(),
                    result.extractedLinks() != null ? result.extractedLinks().size() : 0,
                    result.fetchDurationMs());
        } else {
            log.warn("Failed to crawl: {} - Error: {}",
                    result.getUrl(),
                    result.errorMessage());
        }
    }

    @Override
    public int getPriority() {
        return 1000; // Low priority - log last
    }

    @Override
    public boolean isAsync() {
        return false; // Logging is fast
    }
}

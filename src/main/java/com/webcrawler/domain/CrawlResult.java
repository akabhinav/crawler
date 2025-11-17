package com.webcrawler.domain;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of a crawl operation.
 *
 * @param request The original request
 * @param statusCode HTTP status code
 * @param content The fetched content (HTML, etc.)
 * @param contentType Content-Type header
 * @param headers Response headers
 * @param extractedLinks Links found in the content
 * @param fetchedAt When the content was fetched
 * @param fetchDurationMs Time taken to fetch (milliseconds)
 * @param success Whether the crawl was successful
 * @param errorMessage Error message if failed
 */
public record CrawlResult(
        CrawlRequest request,
        int statusCode,
        String content,
        String contentType,
        Map<String, List<String>> headers,
        List<URI> extractedLinks,
        Instant fetchedAt,
        long fetchDurationMs,
        boolean success,
        String errorMessage
) {

    public CrawlResult {
        headers = headers != null ? Map.copyOf(headers) : Map.of();
        extractedLinks = extractedLinks != null ? List.copyOf(extractedLinks) : List.of();
        fetchedAt = fetchedAt != null ? fetchedAt : Instant.now();
    }

    /**
     * Creates a successful result
     */
    public static CrawlResult success(
            CrawlRequest request,
            int statusCode,
            String content,
            String contentType,
            Map<String, List<String>> headers,
            long fetchDurationMs
    ) {
        return new CrawlResult(
                request,
                statusCode,
                content,
                contentType,
                headers,
                null, // Links will be extracted later
                Instant.now(),
                fetchDurationMs,
                true,
                null
        );
    }

    /**
     * Creates a failed result
     */
    public static CrawlResult failure(
            CrawlRequest request,
            String errorMessage,
            long fetchDurationMs
    ) {
        return new CrawlResult(
                request,
                0,
                null,
                null,
                Map.of(),
                List.of(),
                Instant.now(),
                fetchDurationMs,
                false,
                errorMessage
        );
    }

    /**
     * Creates a new result with extracted links
     */
    public CrawlResult withExtractedLinks(List<URI> links) {
        return new CrawlResult(
                request,
                statusCode,
                content,
                contentType,
                headers,
                links,
                fetchedAt,
                fetchDurationMs,
                success,
                errorMessage
        );
    }

    public boolean isHtml() {
        return contentType != null && contentType.toLowerCase().contains("text/html");
    }

    public URI getUrl() {
        return request.url();
    }
}

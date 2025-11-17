package com.webcrawler.storage;

import com.webcrawler.domain.CrawlResult;

/**
 * Storage Service Interface - Abstraction for storing crawled data.
 *
 * This interface allows pluggable storage backends:
 * - Local filesystem (for development)
 * - Distributed storage (HDFS, S3, etc.)
 * - Databases (PostgreSQL, MongoDB, etc.)
 * - Search engines (Elasticsearch, Solr, etc.)
 *
 * For trillion-scale crawling, use distributed storage with:
 * - Partitioning by URL hash
 * - Compression (gzip, brotli)
 * - Deduplication
 * - Incremental updates
 */
public interface StorageService {

    /**
     * Stores a crawled page
     * @param result The crawl result to store
     * @return true if stored successfully
     */
    boolean store(CrawlResult result);

    /**
     * Checks if a URL has been stored
     * @param url The URL to check
     * @return true if URL exists in storage
     */
    boolean exists(String url);

    /**
     * Retrieves stored content for a URL
     * @param url The URL to retrieve
     * @return The stored content, or null if not found
     */
    String get(String url);

    /**
     * Deletes stored content for a URL
     * @param url The URL to delete
     * @return true if deleted successfully
     */
    boolean delete(String url);

    /**
     * Gets statistics about stored data
     */
    StorageStats getStats();

    record StorageStats(
            long totalDocuments,
            long totalSizeBytes,
            String storageType
    ) {}
}

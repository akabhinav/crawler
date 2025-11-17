package com.webcrawler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for crawl metadata.
 *
 * Provides:
 * - CRUD operations for crawl metadata
 * - Custom queries for analytics
 * - Statistics and aggregations
 */
@Repository
public interface CrawlMetadataRepository extends JpaRepository<CrawlMetadataEntity, Long> {

    /**
     * Find metadata by URL
     */
    Optional<CrawlMetadataEntity> findByUrl(String url);

    /**
     * Find all URLs for a domain
     */
    List<CrawlMetadataEntity> findByDomain(String domain);

    /**
     * Find URLs by status
     */
    List<CrawlMetadataEntity> findByCrawlStatus(CrawlMetadataEntity.CrawlStatus status);

    /**
     * Find failed URLs for retry
     */
    List<CrawlMetadataEntity> findByCrawlStatusAndRetryCountLessThan(
            CrawlMetadataEntity.CrawlStatus status,
            int maxRetries
    );

    /**
     * Find URLs crawled in a time range
     */
    List<CrawlMetadataEntity> findByFetchedAtBetween(Instant start, Instant end);

    /**
     * Count URLs by status
     */
    long countByCrawlStatus(CrawlMetadataEntity.CrawlStatus status);

    /**
     * Count URLs by domain
     */
    long countByDomain(String domain);

    /**
     * Get success rate
     */
    @Query("SELECT COUNT(c) * 100.0 / (SELECT COUNT(cc) FROM CrawlMetadataEntity cc) " +
            "FROM CrawlMetadataEntity c WHERE c.crawlStatus = 'SUCCESS'")
    Double getSuccessRate();

    /**
     * Get average fetch duration
     */
    @Query("SELECT AVG(c.fetchDurationMs) FROM CrawlMetadataEntity c WHERE c.crawlStatus = 'SUCCESS'")
    Double getAverageFetchDuration();

    /**
     * Get total content size
     */
    @Query("SELECT SUM(c.contentLength) FROM CrawlMetadataEntity c WHERE c.crawlStatus = 'SUCCESS'")
    Long getTotalContentSize();

    /**
     * Get top domains by URL count
     */
    @Query("SELECT c.domain, COUNT(c) as cnt FROM CrawlMetadataEntity c " +
            "GROUP BY c.domain ORDER BY cnt DESC")
    List<Object[]> getTopDomains();

    /**
     * Check if URL was already crawled
     */
    boolean existsByUrl(String url);

    /**
     * Delete old metadata (for cleanup)
     */
    void deleteByFetchedAtBefore(Instant cutoffDate);
}

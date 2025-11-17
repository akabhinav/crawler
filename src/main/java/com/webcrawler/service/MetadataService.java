package com.webcrawler.service;

import com.webcrawler.domain.CrawlResult;
import com.webcrawler.repository.CrawlMetadataEntity;
import com.webcrawler.repository.CrawlMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metadata Service - Manages crawl metadata in PostgreSQL.
 *
 * Features:
 * - Stores crawl metadata for analytics
 * - Tracks success/failure rates
 * - Provides statistics and reporting
 * - Enables recrawl logic based on history
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "crawler.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class MetadataService {

    private final CrawlMetadataRepository repository;

    public MetadataService(CrawlMetadataRepository repository) {
        this.repository = repository;
        log.info("Metadata Service initialized with PostgreSQL");
    }

    /**
     * Records crawl result in metadata database
     */
    @Transactional
    public void recordCrawl(CrawlResult result) {
        try {
            String url = result.getUrl().toString();

            // Check if URL already exists
            CrawlMetadataEntity entity = repository.findByUrl(url)
                    .orElse(new CrawlMetadataEntity());

            // Update entity
            entity.setUrl(url);
            entity.setDomain(result.getUrl().getHost());
            entity.setCrawlStatus(result.success() ?
                    CrawlMetadataEntity.CrawlStatus.SUCCESS :
                    CrawlMetadataEntity.CrawlStatus.FAILED);
            entity.setHttpStatusCode(result.statusCode());
            entity.setContentType(result.contentType());
            entity.setContentLength(result.content() != null ? (long) result.content().length() : null);
            entity.setLinksExtracted(result.extractedLinks() != null ? result.extractedLinks().size() : null);
            entity.setDepth(result.request().depth());
            entity.setPriority(result.request().priority());
            entity.setFetchedAt(result.fetchedAt());
            entity.setFetchDurationMs(result.fetchDurationMs());
            entity.setErrorMessage(result.errorMessage());
            entity.setRetryCount(result.request().retryCount());

            repository.save(entity);

            log.trace("Recorded metadata for: {}", url);

        } catch (Exception e) {
            log.error("Error recording metadata for {}: {}", result.getUrl(), e.getMessage());
        }
    }

    /**
     * Checks if a URL was already crawled successfully
     */
    public boolean wasAlreadyCrawled(String url) {
        return repository.findByUrl(url)
                .map(entity -> entity.getCrawlStatus() == CrawlMetadataEntity.CrawlStatus.SUCCESS)
                .orElse(false);
    }

    /**
     * Gets URLs that need to be retried
     */
    public List<String> getUrlsForRetry(int maxRetries) {
        return repository.findByCrawlStatusAndRetryCountLessThan(
                        CrawlMetadataEntity.CrawlStatus.FAILED,
                        maxRetries
                )
                .stream()
                .map(CrawlMetadataEntity::getUrl)
                .collect(Collectors.toList());
    }

    /**
     * Gets crawl statistics
     */
    public CrawlStats getStats() {
        long totalUrls = repository.count();
        long successCount = repository.countByCrawlStatus(CrawlMetadataEntity.CrawlStatus.SUCCESS);
        long failedCount = repository.countByCrawlStatus(CrawlMetadataEntity.CrawlStatus.FAILED);
        Double avgDuration = repository.getAverageFetchDuration();
        Long totalSize = repository.getTotalContentSize();

        return new CrawlStats(
                totalUrls,
                successCount,
                failedCount,
                totalUrls > 0 ? (successCount * 100.0 / totalUrls) : 0.0,
                avgDuration != null ? avgDuration : 0.0,
                totalSize != null ? totalSize : 0L
        );
    }

    /**
     * Gets domain statistics
     */
    public Map<String, Long> getDomainStats() {
        return repository.getTopDomains().stream()
                .limit(100) // Top 100 domains
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }

    public record CrawlStats(
            long totalUrls,
            long successfulUrls,
            long failedUrls,
            double successRate,
            double avgFetchDurationMs,
            long totalContentSizeBytes
    ) {}
}

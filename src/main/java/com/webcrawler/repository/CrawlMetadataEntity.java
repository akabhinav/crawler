package com.webcrawler.repository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA Entity for crawl metadata storage in PostgreSQL.
 *
 * Stores:
 * - URL and crawl status
 * - HTTP status code and content type
 * - Fetch timestamps and duration
 * - Error information
 * - Domain and depth information
 *
 * Useful for:
 * - Tracking crawl history
 * - Monitoring success/failure rates
 * - Implementing recrawl logic
 * - Analytics and reporting
 */
@Entity
@Table(name = "crawl_metadata", indexes = {
        @Index(name = "idx_url", columnList = "url", unique = true),
        @Index(name = "idx_domain", columnList = "domain"),
        @Index(name = "idx_status", columnList = "crawl_status"),
        @Index(name = "idx_fetched_at", columnList = "fetched_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 255)
    private String domain;

    @Column(name = "crawl_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CrawlStatus crawlStatus;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "content_length")
    private Long contentLength;

    @Column(name = "links_extracted")
    private Integer linksExtracted;

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "fetch_duration_ms")
    private Long fetchDurationMs;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum CrawlStatus {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        SKIPPED
    }
}

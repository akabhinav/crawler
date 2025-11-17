package com.webcrawler.controller;

import com.webcrawler.core.CrawlerOrchestrator;
import com.webcrawler.core.UrlFrontier;
import com.webcrawler.metrics.CrawlerMetrics;
import com.webcrawler.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Crawler REST API Controller - Manage and monitor the crawler.
 *
 * Endpoints:
 * - POST /api/crawler/start - Start crawling
 * - POST /api/crawler/stop - Stop crawling
 * - POST /api/crawler/seeds - Add seed URLs
 * - GET /api/crawler/status - Get crawler status
 * - GET /api/crawler/stats - Get detailed statistics
 */
@Slf4j
@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final CrawlerOrchestrator orchestrator;
    private final UrlFrontier urlFrontier;
    private final CrawlerMetrics metrics;
    private final StorageService storageService;

    public CrawlerController(
            CrawlerOrchestrator orchestrator,
            UrlFrontier urlFrontier,
            CrawlerMetrics metrics,
            StorageService storageService
    ) {
        this.orchestrator = orchestrator;
        this.urlFrontier = urlFrontier;
        this.metrics = metrics;
        this.storageService = storageService;
    }

    /**
     * Starts the crawler
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        log.info("Starting crawler via API");

        orchestrator.start();

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Crawler started successfully"
        ));
    }

    /**
     * Stops the crawler
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        log.info("Stopping crawler via API");

        orchestrator.stop();

        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "message", "Crawler stopped successfully"
        ));
    }

    /**
     * Adds seed URLs
     */
    @PostMapping("/seeds")
    public ResponseEntity<Map<String, Object>> addSeeds(@RequestBody SeedRequest request) {
        log.info("Adding {} seed URLs via API", request.urls().size());

        for (String url : request.urls()) {
            orchestrator.addSeed(url);
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", String.format("Added %d seed URLs", request.urls().size()),
                "seeds", request.urls()
        ));
    }

    /**
     * Gets crawler status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean running = orchestrator.isRunning();
        long frontierSize = urlFrontier.size();

        return ResponseEntity.ok(Map.of(
                "running", running,
                "frontierSize", frontierSize,
                "domainCount", urlFrontier.domainCount()
        ));
    }

    /**
     * Gets detailed statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        CrawlerMetrics.CrawlerStats crawlerStats = metrics.getStats();
        UrlFrontier.FrontierStats frontierStats = urlFrontier.getStats();
        StorageService.StorageStats storageStats = storageService.getStats();

        return ResponseEntity.ok(Map.of(
                "crawler", Map.of(
                        "queuedUrls", crawlerStats.queuedUrls(),
                        "dequeuedUrls", crawlerStats.dequeuedUrls(),
                        "duplicateUrls", crawlerStats.duplicateUrls(),
                        "droppedUrls", crawlerStats.droppedUrls(),
                        "successfulFetches", crawlerStats.successfulFetches(),
                        "failedFetches", crawlerStats.failedFetches(),
                        "pagesProcessed", crawlerStats.pagesProcessed(),
                        "linksExtracted", crawlerStats.linksExtracted(),
                        "activeFetches", crawlerStats.activeFetches()
                ),
                "frontier", Map.of(
                        "totalUrls", frontierStats.totalUrls(),
                        "domains", frontierStats.domains(),
                        "readyUrls", frontierStats.readyUrls()
                ),
                "storage", Map.of(
                        "totalDocuments", storageStats.totalDocuments(),
                        "totalSizeBytes", storageStats.totalSizeBytes(),
                        "storageType", storageStats.storageType()
                )
        ));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "version", "1.0.0"
        ));
    }

    public record SeedRequest(List<String> urls) {}
}

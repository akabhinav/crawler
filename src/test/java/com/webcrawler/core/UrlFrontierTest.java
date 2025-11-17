package com.webcrawler.core;

import com.webcrawler.domain.CrawlRequest;
import com.webcrawler.metrics.CrawlerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for URL Frontier
 */
class UrlFrontierTest {

    private UrlFrontier urlFrontier;

    @BeforeEach
    void setUp() {
        CrawlerMetrics metrics = new CrawlerMetrics(new SimpleMeterRegistry());
        urlFrontier = new UrlFrontier(metrics);
    }

    @Test
    void testAddUrl() {
        CrawlRequest request = CrawlRequest.seed("https://example.com");

        boolean added = urlFrontier.add(request);

        assertTrue(added, "URL should be added successfully");
        assertEquals(1, urlFrontier.size(), "Frontier should have 1 URL");
    }

    @Test
    void testDuplicateUrlRejected() {
        CrawlRequest request = CrawlRequest.seed("https://example.com");

        urlFrontier.add(request);
        boolean addedAgain = urlFrontier.add(request);

        assertFalse(addedAgain, "Duplicate URL should be rejected");
        assertEquals(1, urlFrontier.size(), "Frontier should still have 1 URL");
    }

    @Test
    void testNextReturnsUrl() throws InterruptedException {
        CrawlRequest request = CrawlRequest.seed("https://example.com");
        urlFrontier.add(request);

        // Wait a bit for URL to be promoted to ready queue
        Thread.sleep(100);

        Optional<CrawlRequest> next = urlFrontier.next();

        assertTrue(next.isPresent(), "Should return a URL");
        assertEquals("https://example.com", next.get().getUrlString());
    }

    @Test
    void testPolitenessDelay() {
        String domain = "example.com";
        Duration delay = Duration.ofMillis(100);

        urlFrontier.setDomainDelay(domain, delay);

        CrawlRequest request1 = new CrawlRequest(
                URI.create("https://example.com/page1"),
                0, 100, null, null, null, 0
        );

        CrawlRequest request2 = new CrawlRequest(
                URI.create("https://example.com/page2"),
                0, 100, null, null, null, 0
        );

        urlFrontier.add(request1);
        urlFrontier.add(request2);

        assertEquals(2, urlFrontier.size());
    }

    @Test
    void testPriorityOrdering() {
        CrawlRequest lowPriority = new CrawlRequest(
                URI.create("https://example.com/low"),
                0, 10, null, null, null, 0
        );

        CrawlRequest highPriority = new CrawlRequest(
                URI.create("https://example.com/high"),
                0, 100, null, null, null, 0
        );

        urlFrontier.add(lowPriority);
        urlFrontier.add(highPriority);

        // High priority should be processed first
        assertTrue(highPriority.compareTo(lowPriority) < 0,
                "High priority should come before low priority");
    }

    @Test
    void testClear() {
        urlFrontier.add(CrawlRequest.seed("https://example.com"));
        urlFrontier.add(CrawlRequest.seed("https://test.com"));

        urlFrontier.clear();

        assertEquals(0, urlFrontier.size(), "Frontier should be empty after clear");
    }

    @Test
    void testGetStats() {
        urlFrontier.add(CrawlRequest.seed("https://example.com"));

        UrlFrontier.FrontierStats stats = urlFrontier.getStats();

        assertNotNull(stats);
        assertTrue(stats.totalUrls() > 0);
    }
}

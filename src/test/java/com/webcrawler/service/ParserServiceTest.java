package com.webcrawler.service;

import com.webcrawler.domain.CrawlRequest;
import com.webcrawler.domain.CrawlResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Parser Service
 */
class ParserServiceTest {

    private ParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new ParserService();
    }

    @Test
    void testExtractLinks() {
        String html = """
                <html>
                <body>
                    <a href="https://example.com/page1">Link 1</a>
                    <a href="https://example.com/page2">Link 2</a>
                    <a href="relative/path">Relative</a>
                </body>
                </html>
                """;

        CrawlRequest request = CrawlRequest.seed("https://example.com");
        CrawlResult result = CrawlResult.success(
                request,
                200,
                html,
                "text/html",
                Map.of(),
                100
        );

        CrawlResult parsed = parserService.parse(result);

        assertNotNull(parsed.extractedLinks());
        assertTrue(parsed.extractedLinks().size() >= 2,
                "Should extract at least 2 absolute links");
    }

    @Test
    void testExtractTitle() {
        String html = "<html><head><title>Test Page</title></head><body></body></html>";

        String title = parserService.extractTitle(html);

        assertEquals("Test Page", title);
    }

    @Test
    void testExtractText() {
        String html = "<html><body><p>Hello World</p></body></html>";

        String text = parserService.extractText(html);

        assertTrue(text.contains("Hello World"));
    }

    @Test
    void testNonHtmlContentSkipped() {
        CrawlRequest request = CrawlRequest.seed("https://example.com/image.jpg");
        CrawlResult result = CrawlResult.success(
                request,
                200,
                "binary data",
                "image/jpeg",
                Map.of(),
                100
        );

        CrawlResult parsed = parserService.parse(result);

        assertTrue(parsed.extractedLinks().isEmpty(),
                "Should not extract links from non-HTML content");
    }

    @Test
    void testInvalidHtmlHandled() {
        String invalidHtml = "<html><body><p>Unclosed paragraph<a href='test'>Link</html>";

        CrawlRequest request = CrawlRequest.seed("https://example.com");
        CrawlResult result = CrawlResult.success(
                request,
                200,
                invalidHtml,
                "text/html",
                Map.of(),
                100
        );

        // Should not throw exception
        assertDoesNotThrow(() -> parserService.parse(result));
    }
}

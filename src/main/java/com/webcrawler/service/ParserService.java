package com.webcrawler.service;

import com.webcrawler.domain.CrawlResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser Service - Extracts links and content from HTML.
 *
 * Features:
 * - HTML parsing using Jsoup
 * - Link extraction and normalization
 * - Metadata extraction
 * - Content extraction (for indexing)
 * - Extensible for custom parsers
 */
@Slf4j
@Service
public class ParserService {

    /**
     * Parses HTML and extracts links
     */
    public CrawlResult parse(CrawlResult result) {
        if (!result.success() || !result.isHtml()) {
            return result;
        }

        try {
            List<URI> links = extractLinks(result.content(), result.getUrl());

            log.debug("Extracted {} links from {}", links.size(), result.getUrl());

            return result.withExtractedLinks(links);

        } catch (Exception e) {
            log.error("Error parsing {}: {}", result.getUrl(), e.getMessage());
            return result;
        }
    }

    /**
     * Extracts all links from HTML content
     */
    private List<URI> extractLinks(String html, URI baseUrl) {
        List<URI> links = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(html, baseUrl.toString());

            // Extract links from <a> tags
            Elements anchorTags = doc.select("a[href]");
            for (Element link : anchorTags) {
                String href = link.attr("abs:href"); // Get absolute URL
                URI uri = normalizeUrl(href);
                if (uri != null && isValidUrl(uri)) {
                    links.add(uri);
                }
            }

        } catch (Exception e) {
            log.error("Error extracting links from {}: {}", baseUrl, e.getMessage());
        }

        return links;
    }

    /**
     * Normalizes a URL (removes fragments, lowercases domain, etc.)
     */
    private URI normalizeUrl(String url) {
        try {
            URI uri = new URI(url);

            // Remove fragment
            uri = new URI(
                    uri.getScheme() != null ? uri.getScheme().toLowerCase() : null,
                    uri.getUserInfo(),
                    uri.getHost() != null ? uri.getHost().toLowerCase() : null,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null // Remove fragment
            );

            return uri;

        } catch (URISyntaxException e) {
            log.trace("Invalid URL: {}", url);
            return null;
        }
    }

    /**
     * Validates if a URL should be crawled
     */
    private boolean isValidUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            return false;
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }

        String path = uri.getPath();
        if (path != null && isBlockedPath(path)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a path should be blocked (common non-content paths)
     */
    private boolean isBlockedPath(String path) {
        String lowerPath = path.toLowerCase();

        // Block common non-content files
        return lowerPath.endsWith(".jpg") ||
                lowerPath.endsWith(".jpeg") ||
                lowerPath.endsWith(".png") ||
                lowerPath.endsWith(".gif") ||
                lowerPath.endsWith(".pdf") ||
                lowerPath.endsWith(".zip") ||
                lowerPath.endsWith(".exe") ||
                lowerPath.endsWith(".css") ||
                lowerPath.endsWith(".js");
    }

    /**
     * Extracts text content from HTML (for indexing)
     */
    public String extractText(String html) {
        try {
            Document doc = Jsoup.parse(html);
            return doc.body().text();
        } catch (Exception e) {
            log.error("Error extracting text: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extracts title from HTML
     */
    public String extractTitle(String html) {
        try {
            Document doc = Jsoup.parse(html);
            return doc.title();
        } catch (Exception e) {
            log.error("Error extracting title: {}", e.getMessage());
            return "";
        }
    }
}

package com.webcrawler.service;

import com.webcrawler.domain.RobotRules;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Robots.txt Service - Handles robots.txt parsing and caching.
 *
 * Features:
 * - Parses robots.txt using crawler-commons library
 * - Caches rules per domain (reduces repeated fetches)
 * - Respects crawl-delay directives
 * - Handles sitemap discovery
 */
@Slf4j
@Service
public class RobotsTxtService {

    private final FetcherService fetcherService;
    private final SimpleRobotRulesParser robotParser;

    private static final String USER_AGENT = "DistributedCrawler";

    public RobotsTxtService(FetcherService fetcherService) {
        this.fetcherService = fetcherService;
        this.robotParser = new SimpleRobotRulesParser();

        log.info("Robots.txt Service initialized");
    }

    /**
     * Gets robot rules for a domain (cached)
     */
    @Cacheable(value = "robotRules", key = "#domain")
    public RobotRules getRules(String domain) {
        log.debug("Fetching robots.txt rules for: {}", domain);

        try {
            String robotsTxt = fetcherService.fetchRobotsTxt(domain).block();

            if (robotsTxt == null || robotsTxt.isEmpty()) {
                log.debug("No robots.txt found for {}, allowing all", domain);
                return RobotRules.allowAll();
            }

            BaseRobotRules baseRules = robotParser.parseContent(
                    "https://" + domain + "/robots.txt",
                    robotsTxt.getBytes(),
                    "text/plain",
                    USER_AGENT
            );

            boolean allowed = !baseRules.isAllowNone();
            Duration crawlDelay = baseRules.getCrawlDelay() > 0
                    ? Duration.ofMillis((long) (baseRules.getCrawlDelay() * 1000))
                    : Duration.ofMillis(500);

            Set<String> sitemaps = Stream.of(baseRules.getSitemaps())
                    .collect(Collectors.toSet());

            log.info("Parsed robots.txt for {}: allowed={}, delay={}ms, sitemaps={}",
                    domain, allowed, crawlDelay.toMillis(), sitemaps.size());

            return new RobotRules(allowed, Set.of(), crawlDelay, sitemaps);

        } catch (Exception e) {
            log.error("Error fetching robots.txt for {}: {}", domain, e.getMessage());
            // On error, be conservative and allow with default delay
            return RobotRules.allowAll();
        }
    }

    /**
     * Checks if a URL is allowed to be crawled
     */
    public boolean isAllowed(String url) {
        try {
            String domain = extractDomain(url);
            RobotRules rules = getRules(domain);

            if (!rules.allowed()) {
                return false;
            }

            String path = extractPath(url);
            return rules.isPathAllowed(path);

        } catch (Exception e) {
            log.error("Error checking robots.txt for {}: {}", url, e.getMessage());
            return true; // Default to allowing on error
        }
    }

    /**
     * Gets crawl delay for a domain
     */
    public Duration getCrawlDelay(String domain) {
        RobotRules rules = getRules(domain);
        return rules.crawlDelay();
    }

    private String extractDomain(String url) {
        // Simple domain extraction
        return url.replaceFirst("^https?://", "")
                .replaceFirst("/.*$", "");
    }

    private String extractPath(String url) {
        int pathStart = url.indexOf('/', 8); // After "https://"
        return pathStart >= 0 ? url.substring(pathStart) : "/";
    }
}

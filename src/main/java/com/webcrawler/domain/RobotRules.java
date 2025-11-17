package com.webcrawler.domain;

import java.time.Duration;
import java.util.Set;

/**
 * Represents robots.txt rules for a domain.
 *
 * @param allowed Whether crawling is allowed
 * @param disallowedPaths Paths that should not be crawled
 * @param crawlDelay Requested delay between requests
 * @param sitemaps Sitemap URLs
 */
public record RobotRules(
        boolean allowed,
        Set<String> disallowedPaths,
        Duration crawlDelay,
        Set<String> sitemaps
) {

    public RobotRules {
        disallowedPaths = disallowedPaths != null ? Set.copyOf(disallowedPaths) : Set.of();
        sitemaps = sitemaps != null ? Set.copyOf(sitemaps) : Set.of();
        crawlDelay = crawlDelay != null ? crawlDelay : Duration.ofMillis(500);
    }

    public static RobotRules allowAll() {
        return new RobotRules(true, Set.of(), Duration.ofMillis(500), Set.of());
    }

    public static RobotRules denyAll() {
        return new RobotRules(false, Set.of(), Duration.ofMillis(500), Set.of());
    }

    public boolean isPathAllowed(String path) {
        if (!allowed) {
            return false;
        }

        return disallowedPaths.stream()
                .noneMatch(path::startsWith);
    }
}

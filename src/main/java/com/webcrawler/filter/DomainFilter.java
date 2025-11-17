package com.webcrawler.filter;

import com.webcrawler.domain.CrawlRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain Filter - Filters URLs based on allowed/blocked domains.
 *
 * Configure via application.properties:
 * crawler.filter.allowed-domains=example.com,wikipedia.org
 * crawler.filter.blocked-domains=spam.com,malware.com
 */
@Slf4j
@Component
public class DomainFilter implements UrlFilter {

    private final Set<String> allowedDomains;
    private final Set<String> blockedDomains;
    private final boolean hasAllowedDomains;

    public DomainFilter(
            @Value("${crawler.filter.allowed-domains:}") List<String> allowedDomains,
            @Value("${crawler.filter.blocked-domains:}") List<String> blockedDomains
    ) {
        this.allowedDomains = allowedDomains.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        this.blockedDomains = blockedDomains.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        this.hasAllowedDomains = !this.allowedDomains.isEmpty();

        log.info("Domain Filter initialized - allowed: {}, blocked: {}",
                this.allowedDomains.size(), this.blockedDomains.size());
    }

    @Override
    public boolean shouldCrawl(CrawlRequest request) {
        String domain = request.getDomain();
        if (domain == null) {
            return false;
        }

        domain = domain.toLowerCase();

        // Check blocked list first
        if (blockedDomains.contains(domain)) {
            return false;
        }

        // If allow list exists, domain must be in it
        if (hasAllowedDomains) {
            return allowedDomains.contains(domain);
        }

        return true;
    }

    @Override
    public int getPriority() {
        return 10; // High priority - fail fast
    }
}

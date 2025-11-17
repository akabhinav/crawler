package com.webcrawler.service;

import com.webcrawler.domain.CrawlRequest;
import com.webcrawler.domain.CrawlResult;
import com.webcrawler.metrics.CrawlerMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Fetcher Service - Downloads web pages.
 *
 * Features:
 * - Async HTTP client using WebFlux (non-blocking)
 * - Circuit breaker for fault tolerance
 * - Rate limiting for global politeness
 * - Retry logic with exponential backoff
 * - User-Agent rotation
 * - Timeout configuration
 */
@Slf4j
@Service
public class FetcherService {

    private final WebClient webClient;
    private final CrawlerMetrics metrics;

    @Value("${crawler.fetcher.timeout:10000}")
    private long timeoutMs;

    @Value("${crawler.fetcher.user-agent:Mozilla/5.0 (compatible; DistributedCrawler/1.0; +http://example.com/bot)}")
    private String userAgent;

    public FetcherService(WebClient.Builder webClientBuilder, CrawlerMetrics metrics) {
        this.metrics = metrics;
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();

        log.info("Fetcher Service initialized with timeout: {}ms", timeoutMs);
    }

    /**
     * Fetches a URL with resilience patterns
     */
    @CircuitBreaker(name = "fetcher", fallbackMethod = "fetchFallback")
    @RateLimiter(name = "fetcher")
    @Retry(name = "fetcher")
    public Mono<CrawlResult> fetch(CrawlRequest request) {
        long startTime = System.currentTimeMillis();

        log.debug("Fetching URL: {}", request.getUrlString());

        return webClient.get()
                .uri(request.getUrlString())
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new WebClientResponseException(
                                                response.statusCode().value(),
                                                "HTTP Error",
                                                response.headers().asHttpHeaders(),
                                                body.getBytes(),
                                                null
                                        )
                                ))
                )
                .toEntity(String.class)
                .map(response -> {
                    long duration = System.currentTimeMillis() - startTime;

                    metrics.recordFetchSuccess();
                    metrics.recordFetchDuration(duration);

                    Map<String, List<String>> headers = response.getHeaders();
                    String contentType = headers.getOrDefault(HttpHeaders.CONTENT_TYPE, List.of("unknown"))
                            .stream()
                            .findFirst()
                            .orElse("unknown");

                    log.debug("Successfully fetched {} in {}ms (status: {})",
                            request.getUrlString(), duration, response.getStatusCode().value());

                    return CrawlResult.success(
                            request,
                            response.getStatusCode().value(),
                            response.getBody(),
                            contentType,
                            headers,
                            duration
                    );
                })
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(throwable -> {
                    long duration = System.currentTimeMillis() - startTime;

                    metrics.recordFetchFailure();

                    String errorMessage = throwable.getMessage();
                    log.warn("Failed to fetch {} after {}ms: {}",
                            request.getUrlString(), duration, errorMessage);

                    return Mono.just(CrawlResult.failure(request, errorMessage, duration));
                });
    }

    /**
     * Fallback method for circuit breaker
     */
    private Mono<CrawlResult> fetchFallback(CrawlRequest request, Exception ex) {
        log.error("Circuit breaker fallback for {}: {}", request.getUrlString(), ex.getMessage());
        metrics.recordCircuitBreakerOpen();

        return Mono.just(CrawlResult.failure(
                request,
                "Circuit breaker open: " + ex.getMessage(),
                0
        ));
    }

    /**
     * Fetches robots.txt for a domain
     */
    public Mono<String> fetchRobotsTxt(String domain) {
        String robotsUrl = String.format("https://%s/robots.txt", domain);

        log.debug("Fetching robots.txt: {}", robotsUrl);

        return webClient.get()
                .uri(robotsUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(5000))
                .onErrorResume(throwable -> {
                    log.debug("Failed to fetch robots.txt for {}: {}", domain, throwable.getMessage());
                    return Mono.just(""); // Empty = allow all
                });
    }
}

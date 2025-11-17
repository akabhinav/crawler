package com.webcrawler.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience Configuration - Circuit breakers, rate limiters, and retry logic.
 *
 * Provides fault tolerance for:
 * - Network failures
 * - Slow responses
 * - Server errors
 * - Rate limiting
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open circuit at 50% failure rate
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before half-open
                .slidingWindowSize(100) // Consider last 100 calls
                .minimumNumberOfCalls(10) // Need 10 calls before calculating rate
                .permittedNumberOfCallsInHalfOpenState(5) // Allow 5 test calls
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker("fetcher");

        return registry;
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1000) // 1000 requests
                .limitRefreshPeriod(Duration.ofSeconds(1)) // Per second
                .timeoutDuration(Duration.ofSeconds(5)) // Wait up to 5s for permit
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        registry.rateLimiter("fetcher");

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3) // Retry up to 3 times
                .waitDuration(Duration.ofSeconds(2)) // Wait 2s between retries
                .retryExceptions(Exception.class) // Retry on any exception
                .build();

        RetryRegistry registry = RetryRegistry.of(config);
        registry.retry("fetcher");

        return registry;
    }
}

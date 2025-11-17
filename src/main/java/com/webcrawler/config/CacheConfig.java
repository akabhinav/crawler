package com.webcrawler.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration - High-performance caching for robots.txt and other data.
 *
 * Uses Caffeine cache for:
 * - Fast in-memory caching
 * - Automatic eviction
 * - Size-based limits
 * - Time-based expiration
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("robotRules");

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000) // Max 10k domains
                .expireAfterWrite(24, TimeUnit.HOURS) // Refresh daily
                .recordStats());

        return cacheManager;
    }
}

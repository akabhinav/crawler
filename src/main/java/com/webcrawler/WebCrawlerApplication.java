package com.webcrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Distributed Web Crawler.
 *
 * This is a production-ready, trillion-scale web crawler built with:
 * - Java 21 features (Virtual Threads, Pattern Matching, Records)
 * - Spring Boot 3.x for dependency injection and configuration
 * - Reactive programming for high concurrency
 * - Pluggable architecture for extensibility
 *
 * @author World Top 1% Architect
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class WebCrawlerApplication {

    public static void main(String[] args) {
        // Enable virtual threads for massive concurrency
        System.setProperty("spring.threads.virtual.enabled", "true");

        SpringApplication.run(WebCrawlerApplication.class, args);
    }
}

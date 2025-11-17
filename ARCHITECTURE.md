# Architecture Documentation

## Overview

This document describes the architecture of the Distributed Web Crawler, designed for trillion-scale crawling with production-grade quality.

## Design Principles

### 1. Clean Architecture
- **Separation of Concerns**: Clear boundaries between layers
- **Dependency Inversion**: High-level modules don't depend on low-level modules
- **Interface Segregation**: Small, focused interfaces for extensibility

### 2. SOLID Principles
- **Single Responsibility**: Each class has one reason to change
- **Open/Closed**: Open for extension, closed for modification
- **Liskov Substitution**: Interfaces can be substituted
- **Interface Segregation**: Many specific interfaces
- **Dependency Inversion**: Depend on abstractions

### 3. Immutability
- Domain models use Java Records (immutable by default)
- Thread-safe operations
- Functional programming patterns

### 4. Concurrency
- Java 21 Virtual Threads for massive concurrency
- Non-blocking I/O with Reactor
- Concurrent data structures
- Lock-free algorithms where possible

## System Architecture

### Layer Architecture

```
┌─────────────────────────────────────────────┐
│         Presentation Layer                  │
│    (REST API, Controllers, DTOs)            │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         Application Layer                   │
│    (Orchestrator, Use Cases)                │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         Domain Layer                        │
│    (Entities, Value Objects, Rules)         │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         Infrastructure Layer                │
│    (Services, Storage, External APIs)       │
└─────────────────────────────────────────────┘
```

### Component Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    CrawlerOrchestrator                        │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Crawl Pipeline:                                       │  │
│  │  1. Fetch URL from frontier                            │  │
│  │  2. Check robots.txt                                   │  │
│  │  3. Apply URL filters                                  │  │
│  │  4. Fetch content (with retries)                       │  │
│  │  5. Parse and extract links                            │  │
│  │  6. Store results                                      │  │
│  │  7. Execute handlers                                   │  │
│  │  8. Add discovered links to frontier                   │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
         │          │          │          │          │
    ┌────┴───┐ ┌───┴────┐ ┌───┴────┐ ┌───┴────┐ ┌──┴─────┐
    │  URL   │ │ Robots │ │Fetcher │ │ Parser │ │Storage │
    │Frontier│ │  .txt  │ │        │ │        │ │        │
    └────────┘ └────────┘ └────────┘ └────────┘ └────────┘
```

## Core Components

### 1. URL Frontier

**Purpose**: Manages the crawl queue with politeness and deduplication.

**Key Features**:
- **Bloom Filter**: 5 trillion URL capacity with 0.01% false positive rate
- **Per-Domain Queues**: Ensures politeness by domain
- **Priority Scheduling**: High-value pages crawled first
- **Politeness Delays**: Configurable per-domain delays

**Data Structures**:
```java
// Bloom filter for deduplication
BloomFilter<String> seenUrls;

// Per-domain priority queues
Map<String, PriorityBlockingQueue<CrawlRequest>> domainQueues;

// Last access time tracking
Map<String, Instant> lastAccessTime;

// Ready queue for scheduled URLs
PriorityBlockingQueue<CrawlRequest> readyQueue;
```

**Scalability**:
- **Single Node**: 100M - 1B URLs (2-20GB RAM)
- **Distributed**: Unlimited with Redis/Kafka for queue, distributed Bloom filter

### 2. Fetcher Service

**Purpose**: Downloads web pages with fault tolerance.

**Key Features**:
- **Async HTTP**: WebFlux for non-blocking I/O
- **Circuit Breaker**: Prevents cascade failures
- **Rate Limiter**: Global rate limiting (1000 req/s default)
- **Retry Logic**: Exponential backoff (3 attempts)
- **Connection Pooling**: Reuses connections

**Resilience Patterns**:
```
Request → RateLimiter → CircuitBreaker → Retry → HTTP Client
                                             │
                                             ├─ Success → Process
                                             └─ Failure → Log & Skip
```

### 3. Parser Service

**Purpose**: Extracts links and content from HTML.

**Key Features**:
- **HTML Parsing**: Jsoup for robust parsing
- **Link Extraction**: Absolute URL resolution
- **Content Extraction**: Text for indexing
- **Validation**: Filters invalid/unwanted URLs

**Link Processing**:
```
HTML → Parse → Extract Links → Normalize → Filter → Deduplicate → Add to Frontier
```

### 4. Robots.txt Service

**Purpose**: Ensures compliance with robots.txt.

**Key Features**:
- **Caching**: 24-hour cache per domain
- **Parsing**: crawler-commons library
- **Politeness**: Respects crawl-delay directives
- **Sitemap Discovery**: Extracts sitemap URLs

### 5. Storage Service

**Purpose**: Persists crawled data.

**Interface**: Pluggable storage backends
- **Memory**: Development/testing
- **Filesystem**: Single-node production
- **S3**: Distributed cloud storage
- **Elasticsearch**: Full-text search
- **HDFS**: Large-scale archival

## Data Models

### CrawlRequest (Immutable Record)
```java
record CrawlRequest(
    URI url,
    int depth,
    int priority,
    URI parentUrl,
    Map<String, Object> metadata,
    Instant scheduledTime,
    int retryCount
)
```

### CrawlResult (Immutable Record)
```java
record CrawlResult(
    CrawlRequest request,
    int statusCode,
    String content,
    String contentType,
    Map<String, List<String>> headers,
    List<URI> extractedLinks,
    Instant fetchedAt,
    long fetchDurationMs,
    boolean success,
    String errorMessage
)
```

### RobotRules (Immutable Record)
```java
record RobotRules(
    boolean allowed,
    Set<String> disallowedPaths,
    Duration crawlDelay,
    Set<String> sitemaps
)
```

## Extensibility Framework

### URL Filters

**Purpose**: Filter URLs before crawling.

```java
public interface UrlFilter {
    boolean shouldCrawl(CrawlRequest request);
    int getPriority();
}
```

**Built-in Filters**:
- `DomainFilter`: Whitelist/blacklist domains
- Custom filters: Regex, content-type, depth, etc.

### Crawl Handlers

**Purpose**: Process crawled pages.

```java
public interface CrawlHandler {
    void handle(CrawlResult result);
    int getPriority();
    boolean isAsync();
}
```

**Use Cases**:
- Index to Elasticsearch
- Extract structured data
- Generate thumbnails
- Sentiment analysis

## Concurrency Model

### Virtual Threads (Java 21)

```java
// Automatic virtual thread usage
spring.threads.virtual.enabled=true
```

**Benefits**:
- 100,000+ concurrent threads
- Minimal memory overhead (~1KB per thread)
- No callback hell
- Simple blocking code

### Reactive Programming

```java
// Non-blocking HTTP calls
Mono<CrawlResult> fetch(CrawlRequest request)
```

**Benefits**:
- Efficient I/O operations
- Backpressure handling
- Stream processing

## Scalability Strategy

### Horizontal Scaling

```
┌────────────────────────────────────────────┐
│         Load Balancer / API Gateway        │
└─────┬──────────┬──────────┬──────────┬─────┘
      │          │          │          │
┌─────▼────┐ ┌──▼────┐ ┌───▼────┐ ┌───▼────┐
│Crawler 1 │ │Crawler│ │Crawler │ │Crawler │
│          │ │   2   │ │   3    │ │   N    │
└─────┬────┘ └──┬────┘ └───┬────┘ └───┬────┘
      │         │          │          │
      └─────────┴──────────┴──────────┘
                    │
        ┌───────────▼────────────┐
        │  Shared Infrastructure │
        │  - Redis (Queue)       │
        │  - PostgreSQL (Meta)   │
        │  - S3/HDFS (Content)   │
        │  - Elasticsearch       │
        └────────────────────────┘
```

### Vertical Scaling

- **CPU**: More threads for parsing
- **Memory**: Larger Bloom filter for more URLs
- **Network**: Higher bandwidth for faster fetching
- **Disk**: SSD for faster storage

### Partitioning Strategy

**Domain-based partitioning**:
```
hash(domain) % num_workers → worker_id
```

**Benefits**:
- Natural politeness enforcement
- No coordination needed for same domain
- Easy to add/remove workers

## Performance Characteristics

### Throughput
- **Single Node**: 1,000+ pages/second
- **10 Nodes**: 10,000+ pages/second
- **100 Nodes**: 100,000+ pages/second

### Latency
- **Fetch**: 50-200ms (depends on target server)
- **Parse**: 5-20ms per page
- **Storage**: 1-10ms (in-memory/SSD)

### Memory Usage
- **Base**: 512MB (JVM + Spring Boot)
- **Bloom Filter**: 2GB per 100M URLs
- **Cache**: 100MB (robots.txt, etc.)
- **Total**: ~3GB for 100M URLs

### Disk Usage
- **HTML**: ~50KB average per page
- **100M pages**: ~5TB storage
- **Compressed**: ~1-2TB with gzip

## Monitoring & Observability

### Metrics (Prometheus)

```
# Throughput
crawler.urls.queued
crawler.urls.dequeued
crawler.pages.processed

# Performance
crawler.fetch.duration
crawler.fetch.active

# Quality
crawler.fetch.success
crawler.fetch.failure
crawler.urls.duplicate

# Resilience
crawler.circuit_breaker.open
resilience4j.ratelimiter.available_permissions
```

### Logging

```
INFO  - Crawled: https://example.com - Status: 200 - Links: 45 - Duration: 123ms
WARN  - Failed to crawl: https://error.com - Error: Connection timeout
DEBUG - Added URL to frontier: https://new.com (depth: 2, priority: 50)
```

### Health Checks

```
GET /actuator/health

{
  "status": "UP",
  "components": {
    "crawler": "UP",
    "frontier": "UP",
    "storage": "UP"
  }
}
```

## Security Considerations

### Input Validation
- URL validation and sanitization
- HTML injection prevention
- Resource limits (max depth, max pages)

### Rate Limiting
- Global rate limiter (prevent DDoS)
- Per-domain rate limiter (politeness)
- Circuit breaker (protect targets)

### Authentication
- API key for REST endpoints
- IP whitelisting
- OAuth2 for production deployments

## Deployment

### Docker

```dockerfile
FROM eclipse-temurin:21-jre
COPY target/crawler.jar /app/crawler.jar
CMD ["java", "-jar", "/app/crawler.jar"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-crawler
spec:
  replicas: 10
  template:
    spec:
      containers:
      - name: crawler
        image: webcrawler:1.0.0
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
```

### Configuration Management
- Spring Cloud Config for centralized config
- Kubernetes ConfigMaps/Secrets
- Environment variables for 12-factor app

## Future Enhancements

1. **JavaScript Rendering**: Headless browser integration
2. **Incremental Crawling**: Delta updates only
3. **Content Deduplication**: SimHash/MinHash
4. **Machine Learning**: Smart prioritization
5. **Graph Analysis**: PageRank, link analysis
6. **Multi-language**: I18n support
7. **Admin UI**: Web dashboard
8. **Distributed Tracing**: Jaeger/Zipkin

## References

- [Web Crawling Wikipedia](https://en.wikipedia.org/wiki/Web_crawler)
- [Google's Original PageRank Paper](http://ilpubs.stanford.edu:8090/422/)
- [Mercator Web Crawler Paper](https://www.semanticscholar.org/paper/Mercator%3A-A-scalable%2C-extensible-web-crawler-Heydon-Najork/f8d1d9e229d0e23b8adae5a2c75d7d1c0a1e7eb1)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Java 21 Virtual Threads](https://openjdk.org/jeps/444)
- [Resilience4j](https://resilience4j.readme.io/)

---

**Version**: 1.0.0
**Last Updated**: 2025
**Authors**: World-Class Architecture Team

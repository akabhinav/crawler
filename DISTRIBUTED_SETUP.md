# Distributed Setup Guide

Complete guide for running the crawler with **MinIO (S3)**, **Redis**, and **PostgreSQL** for production-grade distributed crawling.

## 🎯 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Web Crawler Instances                     │
│                    (Horizontally Scalable)                   │
└──────┬──────────────┬──────────────┬──────────────┬─────────┘
       │              │              │              │
       │         ┌────▼────────────────────▼────┐   │
       │         │    Redis (URL Queue)         │   │
       │         │  - Distributed frontier      │   │
       │         │  - URL deduplication         │   │
       │         │  - Domain queues             │   │
       │         └──────────────────────────────┘   │
       │                                             │
  ┌────▼─────────────────────┐    ┌────────────────▼─────────┐
  │  PostgreSQL (Metadata)   │    │  MinIO (S3 Storage)      │
  │  - Crawl history         │    │  - HTML content          │
  │  - Success/failure stats │    │  - GZIP compression      │
  │  - Analytics             │    │  - Trillion-scale        │
  └──────────────────────────┘    └──────────────────────────┘
```

## 🚀 Quick Start with Docker Compose

### 1. Start All Services

```bash
# Start all services (PostgreSQL, Redis, MinIO, Crawler, Prometheus, Grafana)
docker-compose up -d

# View logs
docker-compose logs -f crawler

# Check status
docker-compose ps
```

### 2. Access the Services

- **Crawler API**: http://localhost:8080
- **MinIO Console**: http://localhost:9001 (minioadmin / minioadmin)
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin / admin)
- **PostgreSQL**: localhost:5432 (crawler / crawler123)
- **Redis**: localhost:6379

### 3. Start Crawling

```bash
# Add seed URLs
curl -X POST http://localhost:8080/api/crawler/seeds \
  -H "Content-Type: application/json" \
  -d '{"urls": ["https://example.com", "https://wikipedia.org"]}'

# Start crawler
curl -X POST http://localhost:8080/api/crawler/start

# Check stats
curl http://localhost:8080/api/crawler/stats
```

## 📊 Service Details

### PostgreSQL - Metadata Storage

**Purpose**: Stores crawl metadata for analytics and history tracking.

**Schema**:
```sql
crawl_metadata:
  - url (unique)
  - domain
  - crawl_status (SUCCESS, FAILED, etc.)
  - http_status_code
  - content_length
  - links_extracted
  - fetch_duration_ms
  - error_message
  - retry_count
  - timestamps
```

**Connect**:
```bash
# Using psql
psql -h localhost -U crawler -d crawler

# View stats
SELECT crawl_status, COUNT(*)
FROM crawl_metadata
GROUP BY crawl_status;
```

**Queries**:
```sql
-- Success rate
SELECT
    COUNT(CASE WHEN crawl_status = 'SUCCESS' THEN 1 END) * 100.0 / COUNT(*) as success_rate
FROM crawl_metadata;

-- Top domains
SELECT domain, COUNT(*) as url_count
FROM crawl_metadata
GROUP BY domain
ORDER BY url_count DESC
LIMIT 10;

-- Average fetch time
SELECT AVG(fetch_duration_ms)
FROM crawl_metadata
WHERE crawl_status = 'SUCCESS';
```

### Redis - Distributed URL Queue

**Purpose**: Distributed URL frontier for coordinating multiple crawler instances.

**Data Structures**:
```
crawler:urls:seen (SET)              - Deduplicated URLs
crawler:queue:{domain} (ZSET)        - Per-domain priority queues
crawler:domains (SET)                - Active domains
crawler:last-access:{domain} (STR)   - Politeness tracking
crawler:delay:{domain} (STR)         - Domain-specific delays
```

**Connect**:
```bash
# Using redis-cli
redis-cli

# View stats
> SCARD crawler:urls:seen
> SMEMBERS crawler:domains
> ZRANGE crawler:queue:example.com 0 10
```

**Monitoring**:
```bash
# Monitor commands
redis-cli MONITOR

# Get info
redis-cli INFO

# Check memory
redis-cli INFO memory
```

### MinIO - S3-Compatible Object Storage

**Purpose**: Scalable object storage for HTML content and crawled pages.

**Features**:
- S3-compatible API
- GZIP compression (50-80% space savings)
- Hash-based partitioning
- Metadata storage (status codes, content-type, etc.)

**Access MinIO Console**:
1. Open http://localhost:9001
2. Login: minioadmin / minioadmin
3. Browse bucket: crawler-pages

**Object Structure**:
```
crawler-pages/
  ├── example.com/
  │   ├── a1/
  │   │   └── a1b2c3d4...hash.html.gz
  │   └── b2/
  │       └── b2c3d4e5...hash.html.gz
  └── wikipedia.org/
      └── c3/
          └── c3d4e5f6...hash.html.gz
```

**CLI Access**:
```bash
# Install MinIO client
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc

# Configure
./mc alias set local http://localhost:9000 minioadmin minioadmin

# List objects
./mc ls local/crawler-pages

# Download object
./mc cp local/crawler-pages/example.com/a1/hash.html.gz .

# Get stats
./mc admin info local
```

## ⚙️ Configuration Profiles

### Local Development (In-Memory)

```properties
# application.properties
crawler.storage.type=memory
crawler.frontier.type=local
crawler.metadata.enabled=false
```

Run: `./run.sh` or `mvn spring-boot:run`

### Docker Compose (Full Stack)

```properties
# application-docker.properties (auto-loaded)
crawler.storage.type=minio
crawler.frontier.type=redis
crawler.metadata.enabled=true
```

Run: `docker-compose up -d`

### Kubernetes Production

```yaml
# deployment.yaml
env:
  - name: CRAWLER_STORAGE_TYPE
    value: "minio"
  - name: CRAWLER_FRONTIER_TYPE
    value: "redis"
  - name: SPRING_DATASOURCE_URL
    value: "jdbc:postgresql://postgres-service:5432/crawler"
  - name: SPRING_DATA_REDIS_HOST
    value: "redis-service"
  - name: CRAWLER_STORAGE_MINIO_ENDPOINT
    value: "http://minio-service:9000"
```

## 🔧 Scaling Strategies

### Horizontal Scaling (Multiple Crawler Instances)

```bash
# Scale to 5 crawler instances
docker-compose up -d --scale crawler=5
```

**Requirements**:
- Redis for distributed queue coordination
- PostgreSQL for shared metadata
- MinIO for shared storage

**Benefits**:
- 5x throughput (linear scaling)
- Automatic work distribution via Redis
- No duplicate crawling (Redis deduplication)

### Vertical Scaling (Single Instance)

```yaml
# docker-compose.yml
crawler:
  environment:
    - CRAWLER_CONCURRENCY=500  # Increase workers
  deploy:
    resources:
      limits:
        memory: 8G
        cpus: '4'
```

### Database Sharding (Trillion-Scale)

For > 10B URLs, shard PostgreSQL by domain hash:

```sql
-- Shard 1: domains starting with a-m
CREATE TABLE crawl_metadata_shard1 ...

-- Shard 2: domains starting with n-z
CREATE TABLE crawl_metadata_shard2 ...
```

### Redis Cluster (High Availability)

```bash
# Use Redis Cluster for > 100M URLs in queue
redis-cli --cluster create \
  127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 \
  --cluster-replicas 1
```

### MinIO Distributed Mode

```bash
# Run MinIO in distributed mode (4+ nodes)
minio server \
  http://minio{1...4}/data/disk{1...4}
```

## 📈 Monitoring & Observability

### View Metrics in Prometheus

1. Open http://localhost:9090
2. Query examples:
   ```
   crawler_urls_queued
   crawler_fetch_success_total
   crawler_fetch_duration_seconds
   rate(crawler_pages_processed_total[5m])
   ```

### Create Grafana Dashboards

1. Open http://localhost:3000 (admin / admin)
2. Add Prometheus datasource: http://prometheus:9090
3. Import dashboard or create custom:
   - URL queue size over time
   - Success/failure rate
   - Fetch duration histogram
   - Pages per second

### PostgreSQL Analytics

```sql
-- Crawl performance over time
SELECT
    DATE_TRUNC('hour', fetched_at) as hour,
    COUNT(*) as pages_crawled,
    AVG(fetch_duration_ms) as avg_duration
FROM crawl_metadata
WHERE fetched_at > NOW() - INTERVAL '24 hours'
GROUP BY hour
ORDER BY hour;

-- Error analysis
SELECT
    error_message,
    COUNT(*) as count
FROM crawl_metadata
WHERE crawl_status = 'FAILED'
GROUP BY error_message
ORDER BY count DESC
LIMIT 10;
```

## 🛠️ Troubleshooting

### Crawler Can't Connect to Redis

```bash
# Check if Redis is running
docker-compose ps redis

# Test connection
redis-cli -h localhost -p 6379 ping

# Check crawler logs
docker-compose logs crawler | grep -i redis
```

### Crawler Can't Connect to PostgreSQL

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Test connection
psql -h localhost -U crawler -d crawler

# Check crawler logs
docker-compose logs crawler | grep -i postgres
```

### MinIO Bucket Not Created

```bash
# Check MinIO logs
docker-compose logs minio

# Manually create bucket
mc mb local/crawler-pages

# Check crawler logs
docker-compose logs crawler | grep -i minio
```

### Out of Memory

```bash
# Increase crawler memory
docker-compose up -d --scale crawler=1 \
  --set crawler.deploy.resources.limits.memory=8G

# Or reduce concurrency
# Edit application-docker.properties
crawler.concurrency=100
```

### Redis Out of Memory

```bash
# Check Redis memory
redis-cli INFO memory

# Set memory limit
redis-cli CONFIG SET maxmemory 2gb
redis-cli CONFIG SET maxmemory-policy allkeys-lru

# Or clear old data
redis-cli FLUSHDB
```

## 🔐 Production Security

### Change Default Passwords

```yaml
# docker-compose.yml
postgres:
  environment:
    - POSTGRES_PASSWORD=<strong-password>

minio:
  environment:
    - MINIO_ROOT_PASSWORD=<strong-password>

redis:
  command: redis-server --requirepass <strong-password>
```

### Enable TLS/SSL

```yaml
minio:
  environment:
    - MINIO_SERVER_URL=https://minio.example.com
  volumes:
    - ./certs:/root/.minio/certs
```

### Network Isolation

```yaml
services:
  postgres:
    networks:
      - backend

  redis:
    networks:
      - backend

  crawler:
    networks:
      - backend
      - frontend

networks:
  frontend:
  backend:
    internal: true
```

## 📦 Backup & Recovery

### PostgreSQL Backup

```bash
# Backup
docker exec -t postgres pg_dump -U crawler crawler > backup.sql

# Restore
docker exec -i postgres psql -U crawler crawler < backup.sql
```

### Redis Backup

```bash
# Backup (AOF or RDB)
docker exec redis redis-cli BGSAVE

# Copy snapshot
docker cp redis:/data/dump.rdb ./redis-backup.rdb

# Restore
docker cp ./redis-backup.rdb redis:/data/dump.rdb
docker-compose restart redis
```

### MinIO Backup

```bash
# Sync to another MinIO/S3
mc mirror local/crawler-pages remote/crawler-backup

# Or download all
mc cp --recursive local/crawler-pages ./minio-backup/
```

## 🚀 Performance Tuning

### Optimize PostgreSQL

```sql
-- Increase work memory
ALTER SYSTEM SET work_mem = '256MB';

-- Increase shared buffers
ALTER SYSTEM SET shared_buffers = '1GB';

-- Reload config
SELECT pg_reload_conf();
```

### Optimize Redis

```bash
# Increase max connections
redis-cli CONFIG SET maxclients 10000

# Enable pipelining in application
# (done automatically by Spring Data Redis)
```

### Optimize MinIO

```bash
# Set environment variables
MINIO_STORAGE_CLASS_STANDARD=EC:2  # Erasure coding
MINIO_API_REQUESTS_MAX=1000        # Concurrent requests
```

---

**Ready for trillion-scale crawling!** 🎉

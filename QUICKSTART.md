# Quick Start Guide

Get the crawler running in 5 minutes!

## Prerequisites

- Java 21+ ([Download](https://adoptium.net/))
- Maven 3.8+ (or use included `./mvnw`)
- 4GB+ RAM

## Option 1: Quick Local Run

```bash
# 1. Clone and enter directory
git clone <repo-url>
cd crawler

# 2. Run the startup script
./run.sh

# 3. In another terminal, start crawling
curl -X POST http://localhost:8080/api/crawler/seeds \
  -H "Content-Type: application/json" \
  -d '{"urls": ["https://example.com"]}'

curl -X POST http://localhost:8080/api/crawler/start
```

## Option 2: Docker

```bash
# Build and run with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f crawler

# Stop
docker-compose down
```

## Option 3: Manual Build

```bash
# Build
mvn clean package

# Run
java -jar target/distributed-web-crawler-1.0.0.jar

# Or with Maven
mvn spring-boot:run
```

## Basic Usage

### Add Seed URLs
```bash
curl -X POST http://localhost:8080/api/crawler/seeds \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "https://example.com",
      "https://wikipedia.org/wiki/Web_crawler"
    ]
  }'
```

### Start Crawling
```bash
curl -X POST http://localhost:8080/api/crawler/start
```

### Check Status
```bash
# Simple status
curl http://localhost:8080/api/crawler/status

# Detailed statistics
curl http://localhost:8080/api/crawler/stats
```

### Stop Crawling
```bash
curl -X POST http://localhost:8080/api/crawler/stop
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# How many concurrent crawls
crawler.concurrency=100

# Maximum depth to crawl
crawler.max-depth=5

# Respect robots.txt
crawler.enable-robots-txt=true
```

## Monitoring

- **Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:8080/actuator/prometheus
- **Health**: http://localhost:8080/actuator/health

## Common Issues

### "Port 8080 already in use"
Change the port in `application.properties`:
```properties
server.port=8081
```

### "Out of memory"
Increase Java heap:
```bash
java -Xmx4g -jar target/distributed-web-crawler-1.0.0.jar
```

### "Too many open files"
Increase file descriptor limit:
```bash
ulimit -n 65536
```

## Next Steps

1. Read the [README.md](README.md) for full documentation
2. Check [ARCHITECTURE.md](ARCHITECTURE.md) for design details
3. Customize filters and handlers for your use case
4. Set up monitoring with Prometheus + Grafana
5. Deploy to production with Kubernetes

## Examples

### Crawl a specific domain only
```bash
# Edit application.properties
crawler.filter.allowed-domains=example.com,wikipedia.org
```

### Use filesystem storage
```bash
# Edit application.properties
crawler.storage.type=filesystem
crawler.storage.path=/path/to/storage
```

### Increase concurrency
```bash
# Edit application.properties
crawler.concurrency=500
```

## Support

- 📖 Documentation: See README.md
- 🐛 Issues: GitHub Issues
- 💬 Discussions: GitHub Discussions

---

**Happy Crawling! 🕷️**

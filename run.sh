#!/bin/bash

# Web Crawler - Local Run Script
# This script helps you run the crawler locally

set -e

echo "======================================"
echo "  Distributed Web Crawler"
echo "  Java 21 + Spring Boot 3.x"
echo "======================================"
echo ""

# Check Java version
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 21+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Java 21+ required. Found Java $JAVA_VERSION"
    exit 1
fi

echo "✅ Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Build if jar doesn't exist
if [ ! -f "target/distributed-web-crawler-1.0.0.jar" ]; then
    echo "🔨 Building project..."
    ./mvnw clean package -DskipTests
    echo "✅ Build complete"
    echo ""
fi

# Run the application
echo "🚀 Starting Web Crawler..."
echo ""
echo "Access the crawler at: http://localhost:8080"
echo "API Documentation: http://localhost:8080/actuator"
echo "Metrics: http://localhost:8080/actuator/prometheus"
echo ""
echo "Press Ctrl+C to stop"
echo "======================================"
echo ""

java -jar target/distributed-web-crawler-1.0.0.jar

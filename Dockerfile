# Multi-stage build for production-ready Docker image

# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build application (skip tests for faster builds, run tests in CI/CD)
RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests && \
    mv target/*.jar app.jar

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create non-root user for security
RUN groupadd -r crawler && useradd -r -g crawler crawler

# Copy JAR from build stage
COPY --from=build /app/app.jar ./crawler.jar

# Create data directory
RUN mkdir -p /data/crawler && chown -R crawler:crawler /data/crawler

# Switch to non-root user
USER crawler

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Enable virtual threads
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar crawler.jar"]

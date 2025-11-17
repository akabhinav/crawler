package com.webcrawler.storage;

import com.webcrawler.domain.CrawlResult;
import io.minio.*;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MinIO Storage Service - S3-compatible object storage for crawled pages.
 *
 * Features:
 * - Stores HTML content in MinIO (S3-compatible)
 * - Automatic bucket creation
 * - GZIP compression for space efficiency
 * - Path-based organization by domain
 * - Metadata storage (status code, content-type, etc.)
 *
 * Perfect for trillion-scale crawling with distributed object storage.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "crawler.storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final boolean enableCompression;
    private final AtomicLong documentCount = new AtomicLong(0);
    private final AtomicLong totalSize = new AtomicLong(0);

    public MinioStorageService(
            @Value("${crawler.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${crawler.storage.minio.access-key:minioadmin}") String accessKey,
            @Value("${crawler.storage.minio.secret-key:minioadmin}") String secretKey,
            @Value("${crawler.storage.minio.bucket:crawler-pages}") String bucketName,
            @Value("${crawler.storage.minio.compression:true}") boolean enableCompression
    ) {
        this.bucketName = bucketName;
        this.enableCompression = enableCompression;

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        log.info("MinIO Storage Service initialized - endpoint: {}, bucket: {}, compression: {}",
                endpoint, bucketName, enableCompression);
    }

    @PostConstruct
    public void initialize() {
        try {
            // Create bucket if it doesn't exist
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("Created MinIO bucket: {}", bucketName);
            } else {
                log.info("MinIO bucket already exists: {}", bucketName);
            }

        } catch (Exception e) {
            log.error("Failed to initialize MinIO storage: {}", e.getMessage(), e);
            throw new RuntimeException("MinIO initialization failed", e);
        }
    }

    @Override
    public boolean store(CrawlResult result) {
        if (!result.success() || result.content() == null) {
            return false;
        }

        try {
            String objectKey = generateObjectKey(result.getUrl().toString());
            byte[] content = result.content().getBytes(StandardCharsets.UTF_8);

            // Compress if enabled
            if (enableCompression) {
                content = compress(content);
            }

            // Store in MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType(enableCompression ? "application/gzip" : "text/html")
                            .userMetadata(java.util.Map.of(
                                    "original-url", result.getUrl().toString(),
                                    "status-code", String.valueOf(result.statusCode()),
                                    "content-type", result.contentType() != null ? result.contentType() : "unknown",
                                    "fetch-time", result.fetchedAt().toString(),
                                    "compressed", String.valueOf(enableCompression)
                            ))
                            .build()
            );

            documentCount.incrementAndGet();
            totalSize.addAndGet(content.length);

            log.debug("Stored in MinIO: {} -> {} ({} bytes, compressed: {})",
                    result.getUrl(), objectKey, content.length, enableCompression);

            return true;

        } catch (Exception e) {
            log.error("Failed to store {} in MinIO: {}", result.getUrl(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String url) {
        try {
            String objectKey = generateObjectKey(url);

            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

            return true;

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            log.error("Error checking existence of {}: {}", url, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error checking existence of {}: {}", url, e.getMessage());
            return false;
        }
    }

    @Override
    public String get(String url) {
        try {
            String objectKey = generateObjectKey(url);

            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

            byte[] content = response.readAllBytes();
            response.close();

            // Decompress if needed
            String compressed = response.headers().get("x-amz-meta-compressed");
            if ("true".equals(compressed)) {
                content = decompress(content);
            }

            return new String(content, StandardCharsets.UTF_8);

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return null;
            }
            log.error("Error retrieving {} from MinIO: {}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Error retrieving {} from MinIO: {}", url, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean delete(String url) {
        try {
            String objectKey = generateObjectKey(url);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

            documentCount.decrementAndGet();

            log.debug("Deleted from MinIO: {} -> {}", url, objectKey);
            return true;

        } catch (Exception e) {
            log.error("Failed to delete {} from MinIO: {}", url, e.getMessage());
            return false;
        }
    }

    @Override
    public StorageStats getStats() {
        return new StorageStats(
                documentCount.get(),
                totalSize.get(),
                "minio (S3-compatible)"
        );
    }

    /**
     * Generates an object key from a URL using hash-based partitioning
     * Format: domain/hash-prefix/url-hash.html.gz
     */
    private String generateObjectKey(String url) {
        try {
            // Extract domain
            String domain = url.replaceFirst("^https?://", "")
                    .replaceFirst("/.*$", "")
                    .replaceAll("[^a-zA-Z0-9.-]", "_");

            // Generate hash for the URL
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            String hashHex = bytesToHex(hash);

            // Use first 2 chars as partition prefix (256 partitions)
            String prefix = hashHex.substring(0, 2);

            // Object key: domain/prefix/hash.html[.gz]
            String extension = enableCompression ? ".html.gz" : ".html";
            return domain + "/" + prefix + "/" + hashHex + extension;

        } catch (Exception e) {
            log.error("Error generating object key for {}: {}", url, e.getMessage());
            // Fallback to simple encoding
            return "unknown/" + URLEncoder.encode(url, StandardCharsets.UTF_8)
                    .substring(0, Math.min(200, url.length()));
        }
    }

    /**
     * Compresses content using GZIP
     */
    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    /**
     * Decompresses GZIP content
     */
    private byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
        }

        return bos.toByteArray();
    }

    /**
     * Converts bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

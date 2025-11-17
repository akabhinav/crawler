package com.webcrawler.storage;

import com.webcrawler.domain.CrawlResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-Memory Storage Service - For development and testing.
 *
 * WARNING: This is NOT suitable for production trillion-scale crawling!
 * Use for local development only. For production, implement:
 * - FileSystemStorageService (local files)
 * - S3StorageService (AWS S3)
 * - HDFSStorageService (Hadoop)
 * - ElasticsearchStorageService (full-text search)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "crawler.storage.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryStorageService implements StorageService {

    private final ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<>();
    private final AtomicLong totalSize = new AtomicLong(0);

    public InMemoryStorageService() {
        log.warn("Using IN-MEMORY storage - NOT suitable for production!");
        log.info("For production, configure crawler.storage.type to: filesystem, s3, hdfs, or elasticsearch");
    }

    @Override
    public boolean store(CrawlResult result) {
        if (!result.success() || result.content() == null) {
            return false;
        }

        String url = result.getUrl().toString();
        String content = result.content();

        storage.put(url, content);
        totalSize.addAndGet(content.length());

        log.trace("Stored content for: {} ({} bytes)", url, content.length());

        return true;
    }

    @Override
    public boolean exists(String url) {
        return storage.containsKey(url);
    }

    @Override
    public String get(String url) {
        return storage.get(url);
    }

    @Override
    public boolean delete(String url) {
        String content = storage.remove(url);
        if (content != null) {
            totalSize.addAndGet(-content.length());
            return true;
        }
        return false;
    }

    @Override
    public StorageStats getStats() {
        return new StorageStats(
                storage.size(),
                totalSize.get(),
                "in-memory"
        );
    }

    public void clear() {
        storage.clear();
        totalSize.set(0);
        log.info("Storage cleared");
    }
}

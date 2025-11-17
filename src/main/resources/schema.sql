-- PostgreSQL Schema for Crawler Metadata
-- This schema is automatically created by Hibernate (spring.jpa.hibernate.ddl-auto=update)
-- This file is provided for reference and manual setup if needed

CREATE TABLE IF NOT EXISTS crawl_metadata (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    crawl_status VARCHAR(20) NOT NULL,
    http_status_code INTEGER,
    content_type VARCHAR(100),
    content_length BIGINT,
    links_extracted INTEGER,
    depth INTEGER,
    priority INTEGER,
    fetched_at TIMESTAMP,
    fetch_duration_ms BIGINT,
    error_message VARCHAR(1000),
    retry_count INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes for better query performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_url ON crawl_metadata(url);
CREATE INDEX IF NOT EXISTS idx_domain ON crawl_metadata(domain);
CREATE INDEX IF NOT EXISTS idx_status ON crawl_metadata(crawl_status);
CREATE INDEX IF NOT EXISTS idx_fetched_at ON crawl_metadata(fetched_at);

-- Add check constraint for crawl_status
ALTER TABLE crawl_metadata
ADD CONSTRAINT crawl_status_check
CHECK (crawl_status IN ('PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED', 'SKIPPED'));

COMMENT ON TABLE crawl_metadata IS 'Stores metadata for all crawled URLs';
COMMENT ON COLUMN crawl_metadata.url IS 'The crawled URL';
COMMENT ON COLUMN crawl_metadata.domain IS 'Domain extracted from URL';
COMMENT ON COLUMN crawl_metadata.crawl_status IS 'Current status of the crawl';
COMMENT ON COLUMN crawl_metadata.http_status_code IS 'HTTP response status code';
COMMENT ON COLUMN crawl_metadata.content_type IS 'Content-Type header value';
COMMENT ON COLUMN crawl_metadata.content_length IS 'Size of content in bytes';
COMMENT ON COLUMN crawl_metadata.links_extracted IS 'Number of links found in page';
COMMENT ON COLUMN crawl_metadata.depth IS 'Crawl depth from seed URL';
COMMENT ON COLUMN crawl_metadata.priority IS 'Crawl priority (higher = more important)';
COMMENT ON COLUMN crawl_metadata.fetched_at IS 'Timestamp when URL was fetched';
COMMENT ON COLUMN crawl_metadata.fetch_duration_ms IS 'Time taken to fetch in milliseconds';
COMMENT ON COLUMN crawl_metadata.error_message IS 'Error message if crawl failed';
COMMENT ON COLUMN crawl_metadata.retry_count IS 'Number of retry attempts';

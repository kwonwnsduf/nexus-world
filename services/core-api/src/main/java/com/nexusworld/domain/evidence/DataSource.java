package com.nexusworld.domain.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_sources")
public class DataSource {
    @Id private UUID id;
    @Column(name = "source_key", nullable = false, unique = true, length = 160) private String sourceKey;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 24) private SourceType sourceType;
    @Column(nullable = false, length = 500) private String title;
    @Column(length = 240) private String publisher;
    @Column(name = "canonical_uri") private String canonicalUri;
    @Column(name = "source_version", length = 120) private String sourceVersion;
    @Column(length = 160) private String license;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "retrieved_at", nullable = false) private Instant retrievedAt;
    @Column(name = "content_sha256", length = 64) private String contentSha256;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode metadata;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected DataSource() {}

    public DataSource(UUID id, String sourceKey, SourceType sourceType, String title, String publisher,
            String canonicalUri, String sourceVersion, String license, Instant publishedAt, Instant retrievedAt,
            String contentSha256, JsonNode metadata, UUID createdBy, Instant createdAt) {
        this.id = id; this.sourceKey = sourceKey; this.sourceType = sourceType; this.title = title;
        this.publisher = publisher; this.canonicalUri = canonicalUri; this.sourceVersion = sourceVersion;
        this.license = license; this.publishedAt = publishedAt; this.retrievedAt = retrievedAt;
        this.contentSha256 = contentSha256; this.metadata = metadata; this.createdBy = createdBy; this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getSourceKey() { return sourceKey; }
    public SourceType getSourceType() { return sourceType; }
    public String getTitle() { return title; }
    public String getPublisher() { return publisher; }
    public String getCanonicalUri() { return canonicalUri; }
    public String getSourceVersion() { return sourceVersion; }
    public String getLicense() { return license; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getRetrievedAt() { return retrievedAt; }
    public String getContentSha256() { return contentSha256; }
    public JsonNode getMetadata() { return metadata; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}

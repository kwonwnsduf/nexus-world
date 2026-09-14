package com.nexusworld.domain.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "raw_ingestion_payloads")
public class RawIngestionPayload {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id")
  private IngestionRun run;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_system", nullable = false, length = 32)
  private SourceSystem sourceSystem;

  @Column(name = "request_uri", nullable = false)
  private String requestUri;

  @Column(name = "page_number", nullable = false)
  private int pageNumber;

  @Column(name = "media_type", length = 160)
  private String mediaType;

  @Column(name = "content_encoding", length = 40)
  private String contentEncoding;

  @Column(name = "source_version", length = 120)
  private String sourceVersion;

  @Column(name = "retrieved_at", nullable = false)
  private Instant retrievedAt;

  @Column(name = "content_sha256", nullable = false, length = 64)
  private String contentSha256;

  @Column(nullable = false)
  private byte[] content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_metadata", nullable = false, columnDefinition = "jsonb")
  private JsonNode responseMetadata;

  protected RawIngestionPayload() {}

  public RawIngestionPayload(
      UUID id,
      IngestionRun run,
      SourceSystem sourceSystem,
      String requestUri,
      int pageNumber,
      String mediaType,
      String contentEncoding,
      String sourceVersion,
      Instant retrievedAt,
      String contentSha256,
      byte[] content,
      JsonNode responseMetadata) {
    this.id = id;
    this.run = run;
    this.sourceSystem = sourceSystem;
    this.requestUri = requestUri;
    this.pageNumber = pageNumber;
    this.mediaType = mediaType;
    this.contentEncoding = contentEncoding;
    this.sourceVersion = sourceVersion;
    this.retrievedAt = retrievedAt;
    this.contentSha256 = contentSha256;
    this.content = content.clone();
    this.responseMetadata = responseMetadata;
  }

  public UUID getId() {
    return id;
  }

  public byte[] getContent() {
    return content.clone();
  }

  public String getRequestUri() {
    return requestUri;
  }
}

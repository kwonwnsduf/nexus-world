package com.nexusworld.domain.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ingestion_rejections")
public class IngestionRejection {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id")
  private IngestionRun run;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "raw_payload_id")
  private RawIngestionPayload rawPayload;

  @Column(name = "record_locator", nullable = false, length = 512)
  private String recordLocator;

  @Column(name = "error_code", nullable = false, length = 80)
  private String errorCode;

  @Column(name = "error_message", nullable = false)
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rejected_record", columnDefinition = "jsonb")
  private JsonNode rejectedRecord;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IngestionRejection() {}

  public IngestionRejection(
      UUID id,
      IngestionRun run,
      RawIngestionPayload rawPayload,
      String locator,
      String code,
      String message,
      JsonNode record,
      Instant at) {
    this.id = id;
    this.run = run;
    this.rawPayload = rawPayload;
    this.recordLocator = locator;
    this.errorCode = code;
    this.errorMessage = message;
    this.rejectedRecord = record;
    this.createdAt = at;
  }
}

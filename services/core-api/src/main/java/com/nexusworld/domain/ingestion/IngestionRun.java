package com.nexusworld.domain.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ingestion_runs")
public class IngestionRun {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_system", nullable = false, length = 32)
  private SourceSystem sourceSystem;

  @Column(name = "request_key", nullable = false, length = 64)
  private String requestKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_parameters", nullable = false, columnDefinition = "jsonb")
  private JsonNode requestParameters;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private IngestionStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "pages_fetched", nullable = false)
  private int pagesFetched;

  @Column(name = "raw_records", nullable = false)
  private int rawRecords;

  @Column(name = "normalized_records", nullable = false)
  private int normalizedRecords;

  @Column(name = "rejected_records", nullable = false)
  private int rejectedRecords;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "initiated_by", nullable = false)
  private UUID initiatedBy;

  protected IngestionRun() {}

  public IngestionRun(
      UUID id,
      SourceSystem sourceSystem,
      String requestKey,
      JsonNode requestParameters,
      Instant startedAt,
      UUID initiatedBy) {
    this.id = id;
    this.sourceSystem = sourceSystem;
    this.requestKey = requestKey;
    this.requestParameters = requestParameters;
    this.status = IngestionStatus.RUNNING;
    this.startedAt = startedAt;
    this.initiatedBy = initiatedBy;
  }

  public void succeed(Instant at, int pages, int raw, int normalized, int rejected) {
    status = IngestionStatus.SUCCEEDED;
    completedAt = at;
    pagesFetched = pages;
    rawRecords = raw;
    normalizedRecords = normalized;
    rejectedRecords = rejected;
  }

  public void fail(Instant at, String message) {
    status = IngestionStatus.FAILED;
    completedAt = at;
    errorMessage =
        message == null
            ? "Unknown ingestion failure"
            : message.substring(0, Math.min(message.length(), 4000));
  }

  public UUID getId() {
    return id;
  }

  public SourceSystem getSourceSystem() {
    return sourceSystem;
  }

  public String getRequestKey() {
    return requestKey;
  }

  public JsonNode getRequestParameters() {
    return requestParameters;
  }

  public IngestionStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public int getPagesFetched() {
    return pagesFetched;
  }

  public int getRawRecords() {
    return rawRecords;
  }

  public int getNormalizedRecords() {
    return normalizedRecords;
  }

  public int getRejectedRecords() {
    return rejectedRecords;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}

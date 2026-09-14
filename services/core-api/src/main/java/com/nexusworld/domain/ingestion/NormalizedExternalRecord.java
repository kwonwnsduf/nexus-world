package com.nexusworld.domain.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "normalized_external_records")
public class NormalizedExternalRecord {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "run_id")
  private IngestionRun run;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "raw_payload_id")
  private RawIngestionPayload rawPayload;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_system", nullable = false, length = 32)
  private SourceSystem sourceSystem;

  @Column(name = "record_type", nullable = false, length = 80)
  private String recordType;

  @Column(name = "natural_key", nullable = false, length = 512)
  private String naturalKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(name = "country_code", length = 16)
  private String countryCode;

  @Column(name = "country_code_scheme", length = 24)
  private String countryCodeScheme;

  @Column(name = "classification_code", length = 80)
  private String classificationCode;

  @Column(name = "classification_version", length = 40)
  private String classificationVersion;

  @Column(name = "currency_code", length = 12)
  private String currencyCode;

  @Column(name = "unit_code", length = 80)
  private String unitCode;

  @Column(name = "period_start")
  private LocalDate periodStart;

  @Column(name = "period_end")
  private LocalDate periodEnd;

  @Column(name = "observed_at")
  private Instant observedAt;

  @Column(name = "data_version", length = 120)
  private String dataVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode dimensions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode value;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode provenance;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected NormalizedExternalRecord() {}

  public NormalizedExternalRecord(
      UUID id,
      IngestionRun run,
      RawIngestionPayload rawPayload,
      SourceSystem sourceSystem,
      String recordType,
      String naturalKey,
      String fingerprint,
      String countryCode,
      String countryCodeScheme,
      String classificationCode,
      String classificationVersion,
      String currencyCode,
      String unitCode,
      LocalDate periodStart,
      LocalDate periodEnd,
      Instant observedAt,
      String dataVersion,
      JsonNode dimensions,
      JsonNode value,
      JsonNode provenance,
      Instant createdAt) {
    this.id = id;
    this.run = run;
    this.rawPayload = rawPayload;
    this.sourceSystem = sourceSystem;
    this.recordType = recordType;
    this.naturalKey = naturalKey;
    this.fingerprint = fingerprint;
    this.countryCode = countryCode;
    this.countryCodeScheme = countryCodeScheme;
    this.classificationCode = classificationCode;
    this.classificationVersion = classificationVersion;
    this.currencyCode = currencyCode;
    this.unitCode = unitCode;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.observedAt = observedAt;
    this.dataVersion = dataVersion;
    this.dimensions = dimensions;
    this.value = value;
    this.provenance = provenance;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getFingerprint() {
    return fingerprint;
  }
}

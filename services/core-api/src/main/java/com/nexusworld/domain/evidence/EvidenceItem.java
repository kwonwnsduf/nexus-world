package com.nexusworld.domain.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evidence_items")
public class EvidenceItem {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_id", nullable = false) private DataSource source;
    @Enumerated(EnumType.STRING) @Column(name = "evidence_type", nullable = false, length = 24) private EvidenceType evidenceType;
    @Column(name = "claim_text", nullable = false) private String claimText;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode locator;
    @Column private String excerpt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "measured_value", columnDefinition = "jsonb") private JsonNode measuredValue;
    @Column(nullable = false, precision = 4, scale = 3) private BigDecimal confidence;
    @Column(name = "observed_at") private Instant observedAt;
    @Column(name = "valid_from") private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected EvidenceItem() {}
    public EvidenceItem(UUID id, DataSource source, EvidenceType evidenceType, String claimText, JsonNode locator,
            String excerpt, JsonNode measuredValue, BigDecimal confidence, Instant observedAt, Instant validFrom,
            Instant validTo, UUID createdBy, Instant createdAt) {
        this.id=id; this.source=source; this.evidenceType=evidenceType; this.claimText=claimText; this.locator=locator;
        this.excerpt=excerpt; this.measuredValue=measuredValue; this.confidence=confidence; this.observedAt=observedAt;
        this.validFrom=validFrom; this.validTo=validTo; this.createdBy=createdBy; this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public DataSource getSource(){return source;} public EvidenceType getEvidenceType(){return evidenceType;}
    public String getClaimText(){return claimText;} public JsonNode getLocator(){return locator;} public String getExcerpt(){return excerpt;}
    public JsonNode getMeasuredValue(){return measuredValue;} public BigDecimal getConfidence(){return confidence;}
    public Instant getObservedAt(){return observedAt;} public Instant getValidFrom(){return validFrom;} public Instant getValidTo(){return validTo;}
    public UUID getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;}
}

package com.nexusworld.domain.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assumptions")
public class Assumption {
    @Id private UUID id;
    @Column(name="assumption_key",nullable=false,unique=true,length=160) private String assumptionKey;
    @Column(nullable=false,length=80) private String category;
    @Column(nullable=false) private String statement;
    @Column(nullable=false) private String rationale;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="assumed_value",columnDefinition="jsonb") private JsonNode assumedValue;
    @Column(length=80) private String unit;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private AssumptionStatus status;
    @Column(nullable=false,precision=4,scale=3) private BigDecimal confidence;
    @Column(name="valid_from") private Instant validFrom;
    @Column(name="valid_to") private Instant validTo;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected Assumption() {}
    public Assumption(UUID id,String assumptionKey,String category,String statement,String rationale,JsonNode assumedValue,
            String unit,AssumptionStatus status,BigDecimal confidence,Instant validFrom,Instant validTo,UUID createdBy,Instant createdAt){
        this.id=id;this.assumptionKey=assumptionKey;this.category=category;this.statement=statement;this.rationale=rationale;
        this.assumedValue=assumedValue;this.unit=unit;this.status=status;this.confidence=confidence;this.validFrom=validFrom;
        this.validTo=validTo;this.createdBy=createdBy;this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public String getAssumptionKey(){return assumptionKey;} public String getCategory(){return category;}
    public String getStatement(){return statement;} public String getRationale(){return rationale;} public JsonNode getAssumedValue(){return assumedValue;}
    public String getUnit(){return unit;} public AssumptionStatus getStatus(){return status;} public BigDecimal getConfidence(){return confidence;}
    public Instant getValidFrom(){return validFrom;} public Instant getValidTo(){return validTo;} public UUID getCreatedBy(){return createdBy;}
    public Instant getCreatedAt(){return createdAt;}
}

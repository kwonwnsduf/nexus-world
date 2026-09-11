package com.nexusworld.api.evidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.evidence.Assumption;
import com.nexusworld.domain.evidence.AssumptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record AssumptionResponse(String contractVersion,UUID id,String assumptionKey,String category,String statement,String rationale,
        JsonNode assumedValue,String unit,AssumptionStatus status,BigDecimal confidence,Instant validFrom,Instant validTo,UUID createdBy,Instant createdAt){
    public static AssumptionResponse from(Assumption a){return new AssumptionResponse("v1",a.getId(),a.getAssumptionKey(),a.getCategory(),
            a.getStatement(),a.getRationale(),a.getAssumedValue(),a.getUnit(),a.getStatus(),a.getConfidence(),a.getValidFrom(),
            a.getValidTo(),a.getCreatedBy(),a.getCreatedAt());}
}

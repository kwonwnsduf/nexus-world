package com.nexusworld.api.evidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.evidence.EvidenceItem;
import com.nexusworld.domain.evidence.EvidenceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record EvidenceResponse(String contractVersion,UUID id,UUID sourceId,EvidenceType evidenceType,String claimText,JsonNode locator,
        String excerpt,JsonNode measuredValue,BigDecimal confidence,Instant observedAt,Instant validFrom,Instant validTo,UUID createdBy,Instant createdAt){
    public static EvidenceResponse from(EvidenceItem e){return new EvidenceResponse("v1",e.getId(),e.getSource().getId(),e.getEvidenceType(),
            e.getClaimText(),e.getLocator(),e.getExcerpt(),e.getMeasuredValue(),e.getConfidence(),e.getObservedAt(),e.getValidFrom(),
            e.getValidTo(),e.getCreatedBy(),e.getCreatedAt());}
}

package com.nexusworld.api.evidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.evidence.ProvenanceLink;
import java.time.Instant;
import java.util.UUID;
public record ProvenanceLinkResponse(String contractVersion,UUID id,String subjectType,UUID subjectId,String propertyPath,
        UUID evidenceId,UUID assumptionId,JsonNode transformation,UUID createdBy,Instant createdAt){
    public static ProvenanceLinkResponse from(ProvenanceLink p){return new ProvenanceLinkResponse("v1",p.getId(),p.getSubjectType(),
            p.getSubjectId(),p.getPropertyPath(),p.getEvidence()==null?null:p.getEvidence().getId(),
            p.getAssumption()==null?null:p.getAssumption().getId(),p.getTransformation(),p.getCreatedBy(),p.getCreatedAt());}
}

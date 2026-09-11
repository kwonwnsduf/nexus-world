package com.nexusworld.api.evidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.evidence.DataSource;
import com.nexusworld.domain.evidence.SourceType;
import java.time.Instant;
import java.util.UUID;
public record SourceResponse(String contractVersion,UUID id,String sourceKey,SourceType sourceType,String title,String publisher,
        String canonicalUri,String sourceVersion,String license,Instant publishedAt,Instant retrievedAt,String contentSha256,
        JsonNode metadata,UUID createdBy,Instant createdAt){
    public static SourceResponse from(DataSource s){return new SourceResponse("v1",s.getId(),s.getSourceKey(),s.getSourceType(),s.getTitle(),
            s.getPublisher(),s.getCanonicalUri(),s.getSourceVersion(),s.getLicense(),s.getPublishedAt(),s.getRetrievedAt(),
            s.getContentSha256(),s.getMetadata(),s.getCreatedBy(),s.getCreatedAt());}
}

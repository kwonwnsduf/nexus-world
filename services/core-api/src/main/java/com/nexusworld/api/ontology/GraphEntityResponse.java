package com.nexusworld.api.ontology;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.ontology.WorldGraphEntity;
import java.time.Instant;
import java.util.UUID;

public record GraphEntityResponse(
        String contractVersion, UUID id, UUID worldVersionId, String entityType,
        String naturalKey, String displayName, JsonNode attributes,
        Instant validFrom, Instant validTo, UUID createdBy, Instant createdAt) {

    static GraphEntityResponse from(WorldGraphEntity entity) {
        return new GraphEntityResponse(
                "v1", entity.getId(), entity.getWorldVersionId(), entity.getEntityType(),
                entity.getNaturalKey(), entity.getDisplayName(), entity.getAttributes(),
                entity.getValidFrom(), entity.getValidTo(), entity.getCreatedBy(), entity.getCreatedAt());
    }
}

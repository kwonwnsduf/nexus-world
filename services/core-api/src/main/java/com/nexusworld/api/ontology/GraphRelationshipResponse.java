package com.nexusworld.api.ontology;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.ontology.WorldGraphRelationship;
import java.time.Instant;
import java.util.UUID;

public record GraphRelationshipResponse(
        String contractVersion, UUID id, UUID worldVersionId, String relationshipType,
        UUID sourceEntityId, String sourceEntityType,
        UUID targetEntityId, String targetEntityType,
        JsonNode attributes, Instant validFrom, Instant validTo,
        UUID createdBy, Instant createdAt) {

    static GraphRelationshipResponse from(WorldGraphRelationship relationship) {
        return new GraphRelationshipResponse(
                "v1", relationship.getId(), relationship.getWorldVersionId(),
                relationship.getRelationshipType(), relationship.getSourceEntityId(),
                relationship.getSourceEntityType(), relationship.getTargetEntityId(),
                relationship.getTargetEntityType(), relationship.getAttributes(),
                relationship.getValidFrom(), relationship.getValidTo(),
                relationship.getCreatedBy(), relationship.getCreatedAt());
    }
}

package com.nexusworld.domain.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="world_graph_relationships")
public class WorldGraphRelationship {
    @Id private UUID id;
    @Column(name = "world_version_id", nullable = false) private UUID worldVersionId;
    @Column(name = "relationship_type", nullable = false, length = 48) private String relationshipType;
    @Column(name = "source_entity_id", nullable = false) private UUID sourceEntityId;
    @Column(name = "source_entity_type", nullable = false, length = 48) private String sourceEntityType;
    @Column(name = "target_entity_id", nullable = false) private UUID targetEntityId;
    @Column(name = "target_entity_type", nullable = false, length = 48) private String targetEntityType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode attributes;
    @Column(name = "valid_from") private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WorldGraphRelationship() {}

    public WorldGraphRelationship(
            UUID id, UUID worldVersionId, String relationshipType,
            UUID sourceEntityId, String sourceEntityType,
            UUID targetEntityId, String targetEntityType,
            JsonNode attributes, Instant validFrom, Instant validTo,
            UUID createdBy, Instant createdAt) {
        this.id = id;
        this.worldVersionId = worldVersionId;
        this.relationshipType = relationshipType;
        this.sourceEntityId = sourceEntityId;
        this.sourceEntityType = sourceEntityType;
        this.targetEntityId = targetEntityId;
        this.targetEntityType = targetEntityType;
        this.attributes = attributes;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getWorldVersionId() { return worldVersionId; }
    public String getRelationshipType() { return relationshipType; }
    public UUID getSourceEntityId() { return sourceEntityId; }
    public String getSourceEntityType() { return sourceEntityType; }
    public UUID getTargetEntityId() { return targetEntityId; }
    public String getTargetEntityType() { return targetEntityType; }
    public JsonNode getAttributes() { return attributes; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}

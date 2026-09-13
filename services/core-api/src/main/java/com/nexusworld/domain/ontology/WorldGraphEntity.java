package com.nexusworld.domain.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="world_graph_entities")
public class WorldGraphEntity {
    @Id
    private UUID id;

    @Column(name = "world_version_id", nullable = false)
    private UUID worldVersionId;

    @Column(name = "entity_type", nullable = false, length = 48)
    private String entityType;

    @Column(name = "natural_key", nullable = false, length = 200)
    private String naturalKey;

    @Column(name = "display_name", nullable = false, length = 240)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode attributes;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorldGraphEntity() {}

    public WorldGraphEntity(
            UUID id,
            UUID worldVersionId,
            String entityType,
            String naturalKey,
            String displayName,
            JsonNode attributes,
            Instant validFrom,
            Instant validTo,
            UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.worldVersionId = worldVersionId;
        this.entityType = entityType;
        this.naturalKey = naturalKey;
        this.displayName = displayName;
        this.attributes = attributes;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getWorldVersionId() { return worldVersionId; }
    public String getEntityType() { return entityType; }
    public String getNaturalKey() { return naturalKey; }
    public String getDisplayName() { return displayName; }
    public JsonNode getAttributes() { return attributes; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}

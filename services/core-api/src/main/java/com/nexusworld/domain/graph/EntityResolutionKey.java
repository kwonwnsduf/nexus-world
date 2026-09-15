package com.nexusworld.domain.graph;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "entity_resolution_keys")
public class EntityResolutionKey {
  @Id private UUID id;
  @Column(name = "world_version_id", nullable = false) private UUID worldVersionId;
  @Column(name = "entity_id", nullable = false) private UUID entityId;
  @Column(name = "entity_type", nullable = false, length = 48) private String entityType;
  @Column(name = "key_scheme", nullable = false, length = 48) private String keyScheme;
  @Column(name = "normalized_value", nullable = false, length = 512) private String normalizedValue;
  @Column(name = "source_system", length = 32) private String sourceSystem;
  @Column(nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected EntityResolutionKey() {}

  public EntityResolutionKey(UUID id, UUID worldVersionId, UUID entityId, String entityType,
      String keyScheme, String normalizedValue, String sourceSystem, double confidence,
      Instant createdAt) {
    this.id = id;
    this.worldVersionId = worldVersionId;
    this.entityId = entityId;
    this.entityType = entityType;
    this.keyScheme = keyScheme;
    this.normalizedValue = normalizedValue;
    this.sourceSystem = sourceSystem;
    this.confidence = BigDecimal.valueOf(confidence);
    this.createdAt = createdAt;
  }

  public UUID getEntityId() { return entityId; }
  public String getKeyScheme() { return keyScheme; }
  public String getNormalizedValue() { return normalizedValue; }
}

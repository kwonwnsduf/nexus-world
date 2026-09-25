package com.nexusworld.domain.graph;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "entity_name_aliases")
public class EntityNameAlias {
  @Id private UUID id;
  @Column(name = "world_version_id", nullable = false) private UUID worldVersionId;
  @Column(name = "entity_id", nullable = false) private UUID entityId;
  @Column(name = "entity_type", nullable = false, length = 48) private String entityType;
  @Column(name = "alias_value", nullable = false, length = 512) private String aliasValue;
  @Column(name = "normalized_value", nullable = false, length = 512) private String normalizedValue;
  @Column(length = 16) private String locale;
  @Column(name = "source_system", length = 32) private String sourceSystem;
  @Column(nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected EntityNameAlias() {}

  public EntityNameAlias(UUID id, UUID worldVersionId, UUID entityId, String entityType,
      String aliasValue, String normalizedValue, String locale, String sourceSystem,
      double confidence, Instant createdAt) {
    this.id = id; this.worldVersionId = worldVersionId; this.entityId = entityId;
    this.entityType = entityType; this.aliasValue = aliasValue; this.normalizedValue = normalizedValue;
    this.locale = locale; this.sourceSystem = sourceSystem;
    this.confidence = BigDecimal.valueOf(confidence); this.createdAt = createdAt;
  }

  public UUID getEntityId() { return entityId; }
  public String getNormalizedValue() { return normalizedValue; }
}

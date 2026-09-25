package com.nexusworld.domain.graph;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityNameAliasRepository extends JpaRepository<EntityNameAlias, UUID> {
  List<EntityNameAlias> findByWorldVersionIdAndEntityType(UUID worldVersionId, String entityType);
  boolean existsByWorldVersionIdAndEntityIdAndNormalizedValue(
      UUID worldVersionId, UUID entityId, String normalizedValue);
}

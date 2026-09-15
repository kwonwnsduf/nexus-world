package com.nexusworld.domain.graph;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityResolutionKeyRepository extends JpaRepository<EntityResolutionKey, UUID> {
  Optional<EntityResolutionKey> findByWorldVersionIdAndEntityTypeAndKeySchemeAndNormalizedValue(
      UUID worldVersionId, String entityType, String keyScheme, String normalizedValue);
}

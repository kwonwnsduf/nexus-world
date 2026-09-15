package com.nexusworld.application.port;

import com.nexusworld.domain.ontology.WorldGraphEntity;
import com.nexusworld.domain.ontology.WorldGraphRelationship;
import java.util.List;
import java.util.UUID;

public interface GraphProjectionStore {
  ProjectionCounts replaceWorld(UUID worldVersionId, List<WorldGraphEntity> entities,
      List<WorldGraphRelationship> relationships);
  List<GraphPath> paths(UUID worldVersionId, UUID rootEntityId, int maxDepth, int limit);

  record ProjectionCounts(int entityCount, int relationshipCount) {}
  record GraphNode(UUID id, String entityType, String naturalKey, String displayName,
      String attributesJson) {}
  record GraphEdge(UUID id, String relationshipType, UUID sourceEntityId, UUID targetEntityId,
      String attributesJson) {}
  record GraphPath(List<GraphNode> nodes, List<GraphEdge> relationships) {}
}

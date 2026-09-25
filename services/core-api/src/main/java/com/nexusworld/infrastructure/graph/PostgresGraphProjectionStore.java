package com.nexusworld.infrastructure.graph;

import com.nexusworld.application.port.GraphProjectionStore;
import com.nexusworld.domain.ontology.WorldGraphEntity;
import com.nexusworld.domain.ontology.WorldGraphEntityRepository;
import com.nexusworld.domain.ontology.WorldGraphRelationship;
import com.nexusworld.domain.ontology.WorldGraphRelationshipRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Read-only path fallback over the authoritative PostgreSQL graph when Neo4j is disabled. */
@Component
@ConditionalOnProperty(prefix = "nexus.graph", name = "enabled", havingValue = "false",
    matchIfMissing = true)
public class PostgresGraphProjectionStore implements GraphProjectionStore {
  private final WorldGraphEntityRepository entities;
  private final WorldGraphRelationshipRepository relationships;

  public PostgresGraphProjectionStore(WorldGraphEntityRepository entities,
      WorldGraphRelationshipRepository relationships) {
    this.entities = entities;
    this.relationships = relationships;
  }

  @Override
  public ProjectionCounts replaceWorld(UUID worldVersionId, List<WorldGraphEntity> nodes,
      List<WorldGraphRelationship> edges) {
    return new ProjectionCounts(nodes.size(), edges.size());
  }

  @Override
  public List<GraphPath> paths(UUID worldVersionId, UUID rootEntityId, int maxDepth, int limit) {
    Map<UUID, WorldGraphEntity> nodes = new HashMap<>();
    entities.findByWorldVersionIdOrderByEntityTypeAscNaturalKeyAsc(worldVersionId)
        .forEach(node -> nodes.put(node.getId(), node));
    if (!nodes.containsKey(rootEntityId)) return List.of();
    List<WorldGraphRelationship> edges =
        relationships.findByWorldVersionIdOrderByRelationshipTypeAscCreatedAtAsc(worldVersionId);
    Map<UUID, List<WorldGraphRelationship>> adjacency = new HashMap<>();
    for (WorldGraphRelationship edge : edges) {
      adjacency.computeIfAbsent(edge.getSourceEntityId(), ignored -> new ArrayList<>()).add(edge);
      adjacency.computeIfAbsent(edge.getTargetEntityId(), ignored -> new ArrayList<>()).add(edge);
    }
    List<GraphPath> result = new ArrayList<>();
    walk(rootEntityId, maxDepth, limit, nodes, adjacency,
        new ArrayList<>(List.of(rootEntityId)), new ArrayList<>(),
        new HashSet<>(Set.of(rootEntityId)), result);
    return List.copyOf(result);
  }

  private void walk(UUID current, int remaining, int limit, Map<UUID, WorldGraphEntity> nodes,
      Map<UUID, List<WorldGraphRelationship>> adjacency, List<UUID> nodePath,
      List<WorldGraphRelationship> edgePath, Set<UUID> visited, List<GraphPath> result) {
    if (remaining == 0 || result.size() >= limit) return;
    for (WorldGraphRelationship edge : adjacency.getOrDefault(current, List.of())) {
      UUID next = edge.getSourceEntityId().equals(current)
          ? edge.getTargetEntityId() : edge.getSourceEntityId();
      if (!visited.add(next)) continue;
      nodePath.add(next);
      edgePath.add(edge);
      result.add(new GraphPath(nodePath.stream().map(nodes::get).map(this::node).toList(),
          edgePath.stream().map(this::edge).toList()));
      if (result.size() < limit) {
        walk(next, remaining - 1, limit, nodes, adjacency, nodePath, edgePath, visited, result);
      }
      edgePath.remove(edgePath.size() - 1);
      nodePath.remove(nodePath.size() - 1);
      visited.remove(next);
      if (result.size() >= limit) return;
    }
  }

  private GraphNode node(WorldGraphEntity value) {
    return new GraphNode(value.getId(), value.getEntityType(), value.getNaturalKey(),
        value.getDisplayName(), value.getAttributes().toString());
  }

  private GraphEdge edge(WorldGraphRelationship value) {
    return new GraphEdge(value.getId(), value.getRelationshipType(), value.getSourceEntityId(),
        value.getTargetEntityId(), value.getAttributes().toString());
  }
}

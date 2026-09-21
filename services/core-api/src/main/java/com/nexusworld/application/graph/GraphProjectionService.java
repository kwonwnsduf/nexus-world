package com.nexusworld.application.graph;

import com.nexusworld.application.port.GraphProjectionStore;
import com.nexusworld.application.port.GraphEntitySearchStore;
import com.nexusworld.domain.graph.*;
import com.nexusworld.domain.ontology.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GraphProjectionService {
  private final WorldGraphEntityRepository entities;
  private final WorldGraphRelationshipRepository relationships;
  private final GraphProjectionRunRepository runs;
  private final GraphProjectionStore store;
  private final GraphEntitySearchStore search;
  private final Clock clock;

  public GraphProjectionService(WorldGraphEntityRepository entities,
      WorldGraphRelationshipRepository relationships, GraphProjectionRunRepository runs,
      GraphProjectionStore store, GraphEntitySearchStore search, Clock clock) {
    this.entities=entities; this.relationships=relationships; this.runs=runs; this.store=store;
    this.search=search; this.clock=clock;
  }

  public GraphProjectionRun project(UUID worldVersionId, UUID actor) {
    if (!entities.worldVersionExists(worldVersionId)) {
      throw new com.nexusworld.application.ontology.OntologyNotFoundException("World version not found");
    }
    GraphProjectionRun run = runs.save(new GraphProjectionRun(
        UUID.randomUUID(), worldVersionId, clock.instant(), actor));
    try {
      var sourceEntities = entities.findByWorldVersionIdOrderByEntityTypeAscNaturalKeyAsc(worldVersionId);
      var sourceRelationships = relationships.findByWorldVersionIdOrderByRelationshipTypeAscCreatedAtAsc(worldVersionId);
      var counts = store.replaceWorld(worldVersionId, sourceEntities, sourceRelationships);
      run.succeed(counts.entityCount(), counts.relationshipCount(), clock.instant());
    } catch (RuntimeException exception) {
      run.fail(exception.getMessage(), clock.instant());
    }
    return runs.save(run);
  }

  public List<GraphProjectionStore.GraphPath> paths(UUID worldVersionId, UUID root, int maxDepth) {
    if (maxDepth < 1 || maxDepth > 3) throw new IllegalArgumentException("maxDepth must be between 1 and 3");
    WorldGraphEntity rootEntity = entities.findById(root)
        .orElseThrow(() -> new com.nexusworld.application.ontology.OntologyNotFoundException("Graph entity not found"));
    if (!rootEntity.getWorldVersionId().equals(worldVersionId)) {
      throw new com.nexusworld.application.ontology.OntologyNotFoundException("Graph entity not found in world version");
    }
    return store.paths(worldVersionId, root, maxDepth, 100);
  }

  public List<GraphProjectionStore.GraphNode> matchNodes(UUID worldVersionId, String query,
      int limit) {
    if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
    if (query.length() > 500) throw new IllegalArgumentException("query must be at most 500 characters");
    if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit must be between 1 and 20");
    if (!entities.worldVersionExists(worldVersionId)) {
      throw new com.nexusworld.application.ontology.OntologyNotFoundException("World version not found");
    }
    GraphSearchQuery searchQuery = GraphSearchQuery.from(query);
    List<UUID> ids = search.search(worldVersionId, searchQuery.normalized(),
        searchQuery.webSearch(), limit);
    Map<UUID, WorldGraphEntity> found = new HashMap<>();
    entities.findAllById(ids).forEach(entity -> found.put(entity.getId(), entity));
    return ids.stream().map(found::get).filter(Objects::nonNull)
        .map(entity -> new GraphProjectionStore.GraphNode(entity.getId(), entity.getEntityType(),
            entity.getNaturalKey(), entity.getDisplayName(), entity.getAttributes().toString()))
        .toList();
  }
}

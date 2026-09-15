package com.nexusworld.infrastructure.graph;

import com.nexusworld.application.graph.GraphStoreUnavailableException;
import com.nexusworld.application.port.GraphProjectionStore;
import com.nexusworld.domain.ontology.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nexus.graph", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledGraphProjectionStore implements GraphProjectionStore {
  private GraphStoreUnavailableException disabled() {
    return new GraphStoreUnavailableException("Neo4j projection is disabled; set NEO4J_ENABLED=true");
  }
  public ProjectionCounts replaceWorld(UUID id, List<WorldGraphEntity> e, List<WorldGraphRelationship> r) { throw disabled(); }
  public List<GraphPath> paths(UUID world, UUID root, int depth, int limit) { throw disabled(); }
}

package com.nexusworld.infrastructure.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.graph.GraphSearchQuery;
import com.nexusworld.application.model.ScenarioWorkflowRecords.ResolvedTarget;
import com.nexusworld.application.model.SimulationRecords.WorldVersion;
import com.nexusworld.application.port.GraphEntitySearchStore;
import com.nexusworld.application.port.ScenarioWorldStore;
import com.nexusworld.application.simulation.SimulationNotFoundException;
import com.nexusworld.domain.ontology.WorldGraphEntityRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcScenarioWorldStore implements ScenarioWorldStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final GraphEntitySearchStore search;
  private final WorldGraphEntityRepository entities;

  public JdbcScenarioWorldStore(JdbcTemplate jdbc, ObjectMapper json,
      GraphEntitySearchStore search, WorldGraphEntityRepository entities) {
    this.jdbc = jdbc;
    this.json = json;
    this.search = search;
    this.entities = entities;
  }

  @Override
  public WorldVersion latestReadyVersion() {
    return jdbc.query("""
        SELECT w.id,w.name,v.id,v.state FROM world_versions v
        JOIN worlds w ON w.id=v.world_id
        WHERE v.state ? 'baselineStatus'
        ORDER BY v.created_at DESC LIMIT 1
        """, (result, row) -> new WorldVersion(result.getObject(1, UUID.class),
            result.getObject(3, UUID.class), result.getString(2), read(result.getString(4))))
        .stream().findFirst().orElseThrow(() -> new SimulationNotFoundException(
            "No data-built READY world version exists; run ingestion and baseline build first"));
  }

  @Override
  public ResolvedTarget resolve(WorldVersion world, String target) {
    GraphSearchQuery query = GraphSearchQuery.from(target);
    UUID id = search.search(world.versionId(), query.normalized(), query.webSearch(), 2)
        .stream().findFirst().orElseThrow(() -> new SimulationNotFoundException(
            "No entity in the selected world version matches the scenario target"));
    var entity = entities.findById(id).orElseThrow();
    if (!entity.getWorldVersionId().equals(world.versionId())) {
      throw new IllegalStateException("Entity resolution crossed world-version boundaries");
    }
    return new ResolvedTarget(entity.getId(), entity.getEntityType(), entity.getNaturalKey(),
        entity.getDisplayName());
  }

  private JsonNode read(String value) {
    try {
      return json.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Persisted world state is invalid JSON", exception);
    }
  }
}

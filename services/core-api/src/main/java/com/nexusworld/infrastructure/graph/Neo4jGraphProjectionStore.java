package com.nexusworld.infrastructure.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.graph.GraphStoreUnavailableException;
import com.nexusworld.application.port.GraphProjectionStore;
import com.nexusworld.config.GraphProperties;
import com.nexusworld.domain.ontology.*;
import jakarta.annotation.PreDestroy;
import java.util.*;
import org.neo4j.driver.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nexus.graph", name = "enabled", havingValue = "true")
public class Neo4jGraphProjectionStore implements GraphProjectionStore {
  private final Driver driver;
  private final String database;
  private final int configuredLimit;
  private final ObjectMapper json;

  public Neo4jGraphProjectionStore(GraphProperties properties, ObjectMapper json) {
    this.driver = GraphDatabase.driver(properties.uri(),
        AuthTokens.basic(properties.username(), properties.password()));
    this.database = properties.database();
    this.configuredLimit = properties.queryLimit();
    this.json = json;
  }

  @Override
  public ProjectionCounts replaceWorld(UUID worldVersionId, List<WorldGraphEntity> entities,
      List<WorldGraphRelationship> relationships) {
    List<Map<String,Object>> nodes = entities.stream().map(this::node).toList();
    List<Map<String,Object>> edges = relationships.stream().map(this::edge).toList();
    try (Session session = driver.session(SessionConfig.forDatabase(database))) {
      session.run("CREATE CONSTRAINT world_entity_identity IF NOT EXISTS "
          + "FOR (n:WorldEntity) REQUIRE (n.worldVersionId, n.id) IS UNIQUE").consume();
      session.executeWrite(tx -> {
        tx.run("MATCH (n:WorldEntity {worldVersionId: $worldVersionId}) DETACH DELETE n",
            Values.parameters("worldVersionId", worldVersionId.toString())).consume();
        tx.run("UNWIND $nodes AS row CREATE (n:WorldEntity) SET n = row", Map.of("nodes", nodes)).consume();
        tx.run("UNWIND $edges AS row "
            + "MATCH (source:WorldEntity {worldVersionId: row.worldVersionId, id: row.sourceEntityId}) "
            + "MATCH (target:WorldEntity {worldVersionId: row.worldVersionId, id: row.targetEntityId}) "
            + "CREATE (source)-[r:WORLD_RELATIONSHIP]->(target) SET r = row", Map.of("edges", edges)).consume();
        return null;
      });
      return new ProjectionCounts(nodes.size(), edges.size());
    } catch (org.neo4j.driver.exceptions.Neo4jException exception) {
      throw new GraphStoreUnavailableException("Neo4j projection failed", exception);
    }
  }

  @Override
  public List<GraphPath> paths(UUID worldVersionId, UUID rootEntityId, int maxDepth, int limit) {
    int safeLimit = Math.min(limit, configuredLimit);
    String cypher = "MATCH p=(root:WorldEntity {worldVersionId: $worldVersionId, id: $rootId})"
        + "-[*1.." + maxDepth + "]-(neighbor:WorldEntity) "
        + "WHERE all(n IN nodes(p) WHERE n.worldVersionId = $worldVersionId) "
        + "RETURN [n IN nodes(p) | {id:n.id, entityType:n.entityType, naturalKey:n.naturalKey, "
        + "displayName:n.displayName, attributesJson:n.attributesJson}] AS nodes, "
        + "[r IN relationships(p) | {id:r.id, relationshipType:r.relationshipType, "
        + "sourceEntityId:r.sourceEntityId, targetEntityId:r.targetEntityId, "
        + "attributesJson:r.attributesJson}] AS relationships "
        + "ORDER BY length(p), [n IN nodes(p) | n.id] LIMIT $limit";
    try (Session session = driver.session(SessionConfig.forDatabase(database))) {
      return session.run(cypher, Values.parameters("worldVersionId", worldVersionId.toString(),
              "rootId", rootEntityId.toString(), "limit", safeLimit))
          .list(record -> new GraphPath(
              record.get("nodes").asList(this::readNode),
              record.get("relationships").asList(this::readEdge)));
    } catch (org.neo4j.driver.exceptions.Neo4jException exception) {
      throw new GraphStoreUnavailableException("Neo4j path query failed", exception);
    }
  }

  private Map<String,Object> node(WorldGraphEntity entity) {
    Map<String,Object> row = new HashMap<>();
    row.put("id", entity.getId().toString()); row.put("worldVersionId", entity.getWorldVersionId().toString());
    row.put("entityType", entity.getEntityType()); row.put("naturalKey", entity.getNaturalKey());
    row.put("displayName", entity.getDisplayName()); row.put("attributesJson", json(entity.getAttributes()));
    if (entity.getValidFrom()!=null) row.put("validFrom", entity.getValidFrom().toString());
    if (entity.getValidTo()!=null) row.put("validTo", entity.getValidTo().toString());
    return row;
  }
  private Map<String,Object> edge(WorldGraphRelationship edge) {
    Map<String,Object> row = new HashMap<>();
    row.put("id", edge.getId().toString()); row.put("worldVersionId", edge.getWorldVersionId().toString());
    row.put("relationshipType", edge.getRelationshipType());
    row.put("sourceEntityId", edge.getSourceEntityId().toString());
    row.put("targetEntityId", edge.getTargetEntityId().toString());
    row.put("attributesJson", json(edge.getAttributes()));
    if (edge.getValidFrom()!=null) row.put("validFrom", edge.getValidFrom().toString());
    if (edge.getValidTo()!=null) row.put("validTo", edge.getValidTo().toString());
    return row;
  }
  private GraphNode readNode(Value value) {
    return new GraphNode(UUID.fromString(value.get("id").asString()), value.get("entityType").asString(),
        value.get("naturalKey").asString(), value.get("displayName").asString(),
        value.get("attributesJson").asString("{}"));
  }
  private GraphEdge readEdge(Value value) {
    return new GraphEdge(UUID.fromString(value.get("id").asString()),
        value.get("relationshipType").asString(), UUID.fromString(value.get("sourceEntityId").asString()),
        UUID.fromString(value.get("targetEntityId").asString()), value.get("attributesJson").asString("{}"));
  }
  private String json(Object value) {
    try { return json.writeValueAsString(value); }
    catch (JsonProcessingException exception) { throw new IllegalArgumentException("Graph attributes are not serializable", exception); }
  }
  @PreDestroy void close() { driver.close(); }
}

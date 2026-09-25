package com.nexusworld.application.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexusworld.application.port.GraphProjectionStore;
import com.nexusworld.config.GraphProperties;
import com.nexusworld.domain.ontology.WorldGraphEntity;
import com.nexusworld.domain.ontology.WorldGraphEntityRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Builds a simulation subgraph from bounded paths read from the Neo4j projection. */
@Service
public class Neo4jSimulationGraphExpander {
  private static final Set<String> QUANTITATIVE_TYPES = Set.of(
      "TRADE_FLOW", "SUPPLIES", "DEPENDS_ON", "PRODUCES", "SHIPS_VIA");

  private final GraphProjectionStore graph;
  private final WorldGraphEntityRepository entities;
  private final GraphProperties properties;
  private final ObjectMapper json;

  public Neo4jSimulationGraphExpander(GraphProjectionStore graph,
      WorldGraphEntityRepository entities, GraphProperties properties, ObjectMapper json) {
    this.graph = graph;
    this.entities = entities;
    this.properties = properties;
    this.json = json;
  }

  public ExpandedGraph expand(UUID worldVersionId, JsonNode baseline, JsonNode shocks,
      int maxDepth) {
    if (!properties.enabled()) {
      throw new IllegalArgumentException(
          "NEO4J_PATHS traversal requires NEO4J_ENABLED=true and a completed graph projection");
    }
    if (maxDepth < 1 || maxDepth > 3) {
      throw new IllegalArgumentException("Neo4j simulation maxDepth must be between 1 and 3");
    }

    Map<String, JsonNode> baselineNodes = new LinkedHashMap<>();
    baseline.path("nodes").forEach(node ->
        baselineNodes.put(node.path("entityId").asText(), node));
    Map<Signature, JsonNode> baselineEdges = new LinkedHashMap<>();
    baseline.path("relationships").forEach(edge -> baselineEdges.put(
        new Signature(edge.path("sourceEntityId").asText(),
            edge.path("targetEntityId").asText(), edge.path("relationshipType").asText()), edge));

    LinkedHashSet<Signature> selected = new LinkedHashSet<>();
    LinkedHashMap<Signature, JsonNode> projected = new LinkedHashMap<>();
    LinkedHashSet<String> roots = new LinkedHashSet<>();
    shocks.forEach(shock -> roots.add(shock.path("entityId").asText()));
    for (String rootNaturalKey : roots) {
      WorldGraphEntity root = entities
          .findByWorldVersionIdAndNaturalKey(worldVersionId, rootNaturalKey)
          .orElseThrow(() -> new IllegalArgumentException(
              "Neo4j traversal root is not an entity in this world version: " + rootNaturalKey));
      for (GraphProjectionStore.GraphPath path :
          graph.paths(worldVersionId, root.getId(), maxDepth, properties.queryLimit())) {
        Map<UUID, String> naturalKeys = new HashMap<>();
        path.nodes().forEach(node -> naturalKeys.put(node.id(), node.naturalKey()));
        for (GraphProjectionStore.GraphEdge edge : path.relationships()) {
          String source = naturalKeys.get(edge.sourceEntityId());
          String target = naturalKeys.get(edge.targetEntityId());
          if (source == null || target == null || !baselineNodes.containsKey(source)
              || !baselineNodes.containsKey(target)
              || !QUANTITATIVE_TYPES.contains(edge.relationshipType())) continue;
          Signature signature = new Signature(source, target, edge.relationshipType());
          selected.add(signature);
          if (!baselineEdges.containsKey(signature)) {
            JsonNode attributes = readObject(edge.attributesJson());
            JsonNode parameters = attributes.path("parameters");
            if (parameters.path("dependencyRatio").isNumber()) {
              projected.put(signature, projectedEdge(edge, signature, parameters, attributes));
            }
          }
        }
      }
    }

    ArrayNode relationships = json.createArrayNode();
    selected.forEach(signature -> {
      JsonNode edge = baselineEdges.get(signature);
      if (edge == null) edge = projected.get(signature);
      if (edge != null) relationships.add(edge.deepCopy());
    });
    if (!roots.isEmpty() && relationships.isEmpty()) {
      throw new IllegalArgumentException(
          "Neo4j paths contained no grounded quantitative relationships for the requested shocks");
    }
    return new ExpandedGraph(baseline.path("nodes").deepCopy(), relationships,
        List.copyOf(roots), maxDepth);
  }

  private ObjectNode projectedEdge(GraphProjectionStore.GraphEdge edge, Signature signature,
      JsonNode parameters, JsonNode attributes) {
    ObjectNode value = json.createObjectNode();
    value.put("relationshipId", edge.id().toString());
    value.put("relationshipType", signature.type());
    value.put("sourceEntityId", signature.source());
    value.put("targetEntityId", signature.target());
    value.set("parameters", parameters.deepCopy());
    ObjectNode provenance = value.putObject("provenance");
    provenance.put("graphSource", "NEO4J_PROJECTION");
    provenance.put("projectedRelationshipId", edge.id().toString());
    if (attributes.has("evidenceId")) provenance.set("evidenceId", attributes.get("evidenceId"));
    if (attributes.has("dataSourceId")) {
      provenance.set("dataSourceId", attributes.get("dataSourceId"));
    }
    return value;
  }

  private JsonNode readObject(String value) {
    try {
      JsonNode parsed = json.readTree(value);
      return parsed != null && parsed.isObject() ? parsed : json.createObjectNode();
    } catch (Exception exception) {
      throw new IllegalArgumentException("Neo4j relationship attributes are invalid JSON", exception);
    }
  }

  private record Signature(String source, String target, String type) {}

  public record ExpandedGraph(JsonNode nodes, JsonNode relationships, List<String> roots,
      int maxDepth) {}
}

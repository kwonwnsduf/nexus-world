package com.nexusworld.api.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.port.GraphProjectionStore;
import java.util.List;
import java.util.UUID;

public record GraphNodeMatchesResponse(String contractVersion, UUID worldVersionId, String query,
    List<Node> matches) {
  static GraphNodeMatchesResponse from(UUID worldVersionId, String query,
      List<GraphProjectionStore.GraphNode> nodes, ObjectMapper json) {
    return new GraphNodeMatchesResponse("v1", worldVersionId, query, nodes.stream()
        .map(node -> new Node(node.id(), node.entityType(), node.naturalKey(), node.displayName(),
            read(json, node.attributesJson())))
        .toList());
  }

  private static JsonNode read(ObjectMapper json, String value) {
    try { return json.readTree(value); }
    catch (Exception exception) { throw new IllegalStateException("Invalid projected attributes", exception); }
  }

  public record Node(UUID id, String entityType, String naturalKey, String displayName,
      JsonNode attributes) {}
}

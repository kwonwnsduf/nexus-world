package com.nexusworld.api.ontology;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.port.GraphProjectionStore;
import java.util.*;
public record GraphPathsResponse(String contractVersion, UUID worldVersionId, UUID rootEntityId,
    int maxDepth, List<Path> paths) {
  public static GraphPathsResponse from(UUID world, UUID root, int depth,
      List<GraphProjectionStore.GraphPath> values, ObjectMapper json) {
    return new GraphPathsResponse("v1", world, root, depth, values.stream().map(path ->
        new Path(path.nodes().stream().map(n -> new Node(n.id(), n.entityType(), n.naturalKey(),
            n.displayName(), read(json, n.attributesJson()))).toList(),
            path.relationships().stream().map(r -> new Relationship(r.id(), r.relationshipType(),
                r.sourceEntityId(), r.targetEntityId(), read(json, r.attributesJson()))).toList())).toList());
  }
  private static JsonNode read(ObjectMapper json, String value) {
    try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException("Invalid projected attributes", e); }
  }
  public record Path(List<Node> nodes, List<Relationship> relationships) {}
  public record Node(UUID id, String entityType, String naturalKey, String displayName, JsonNode attributes) {}
  public record Relationship(UUID id, String relationshipType, UUID sourceEntityId, UUID targetEntityId, JsonNode attributes) {}
}

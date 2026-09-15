package com.nexusworld.api.ontology;
import com.nexusworld.application.graph.EntityResolutionResult;
import java.util.List;
public record EntityResolutionResponse(String contractVersion, String decision,
    GraphEntityResponse entity, List<String> matchedSchemes) {
  static EntityResolutionResponse from(EntityResolutionResult result) {
    return new EntityResolutionResponse("v1", result.decision(), GraphEntityResponse.from(result.entity()), result.matchedSchemes());
  }
}

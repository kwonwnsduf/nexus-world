package com.nexusworld.application.model;

import com.nexusworld.application.model.SimulationRecords.ParallelResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public final class ScenarioWorkflowRecords {
  private ScenarioWorkflowRecords() {}

  public record ScenarioCandidate(String contractVersion, String originalQuery, String target,
      String metric, String changeType, double change, Integer duration, String optionalPolicy,
      double confidence) {}

  public record ResolvedTarget(UUID entityId, String entityType, String naturalKey,
      String displayName) {}

  public record ShockDefinition(UUID targetEntityId, String targetNaturalKey, String metric,
      double change, Integer duration, String basisType) {}

  public record ScenarioWorkflowResult(String contractVersion, String status,
      UUID worldVersionId, ScenarioCandidate extraction, ResolvedTarget target,
      ShockDefinition shock, JsonNode worldManifest, JsonNode provenance,
      JsonNode graphRag, JsonNode dataQuality,
      ParallelResult simulation) {}
}

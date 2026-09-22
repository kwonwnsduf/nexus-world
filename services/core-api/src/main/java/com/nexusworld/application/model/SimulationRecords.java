package com.nexusworld.application.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SimulationRecords {
  private SimulationRecords() {}

  public record WorldVersion(UUID worldId, UUID versionId, String name, JsonNode state) {}
  public record BranchInput(String name, JsonNode input) {}
  public record Branch(UUID id, String name, JsonNode input) {}
  public record ParallelSetup(UUID scenarioId, UUID worldId, UUID worldVersionId,
      List<Branch> branches) {}
  public record EngineResult(JsonNode response, String resultHash, JsonNode finalState,
      JsonNode invariants, List<JsonNode> snapshots) {}
  public record Run(UUID id, UUID branchId, String branchName, long seed, int turns,
      String status, String resultHash, JsonNode finalState, JsonNode invariants,
      String failureReason, Instant requestedAt, Instant completedAt, List<JsonNode> snapshots) {}
  public record ParallelResult(UUID scenarioId, UUID worldId, UUID worldVersionId,
      List<Run> runs) {}
}

package com.nexusworld.api.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ParallelSimulationRequest(
    @NotBlank @Size(max = 160) String scenarioName,
    long seed,
    @Min(1) @Max(120) int turns,
    @Valid GraphTraversal graphTraversal,
    @NotNull @Size(min = 2, max = 3) List<@Valid BranchRequest> branches) {
  public record GraphTraversal(
      @NotNull TraversalMode mode,
      @Min(1) @Max(120) int maxDepth) {}

  public enum TraversalMode { WORLD_VERSION, NEO4J_PATHS }

  public record BranchRequest(
      @NotBlank @Size(max = 160) String name,
      @NotNull JsonNode shocks) {}
}

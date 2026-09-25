package com.nexusworld.application.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.application.model.SimulationRecords.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SimulationStore {
  WorldVersion createWorld(String name, JsonNode state, Instant now);
  WorldVersion createNextWorldVersion(String worldName, JsonNode state, Instant now);
  boolean hasWorldSnapshot(String worldName, String snapshotFingerprint);
  WorldVersion getWorldVersion(UUID versionId);
  ParallelSetup createParallelSetup(UUID versionId, String scenarioName,
      List<BranchInput> branches, Instant now);
  UUID createRun(UUID branchId, long seed, int turns, String requestHash, Instant now);
  void completeRun(UUID runId, EngineResult result, Instant now);
  void failRun(UUID runId, String reason, Instant now);
  ParallelResult getParallelResult(UUID scenarioId);
}

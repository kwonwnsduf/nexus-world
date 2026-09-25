package com.nexusworld.application.port;

import com.nexusworld.application.model.ScenarioWorkflowRecords.ResolvedTarget;
import com.nexusworld.application.model.SimulationRecords.WorldVersion;

public interface ScenarioWorldStore {
  WorldVersion latestReadyVersion();
  ResolvedTarget resolve(WorldVersion world, String target);
}

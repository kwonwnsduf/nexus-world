package com.nexusworld.application.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.application.model.SimulationRecords.EngineResult;

public interface IndustrialSimulationClient {
  EngineResult execute(JsonNode request);
}

package com.nexusworld.application.port;

import com.nexusworld.application.model.ScenarioWorkflowRecords.ScenarioCandidate;

public interface ScenarioInterpreter {
  ScenarioCandidate interpret(String query);
}

package com.nexusworld.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.model.ScenarioWorkflowRecords.*;
import com.nexusworld.application.model.SimulationRecords.*;
import com.nexusworld.application.port.GraphRagClient;
import com.nexusworld.application.port.ScenarioInterpreter;
import com.nexusworld.application.port.ScenarioWorldStore;
import com.nexusworld.application.simulation.NaturalLanguageScenarioService;
import com.nexusworld.application.simulation.ParallelSimulationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NaturalLanguageScenarioServiceTest {
  @Test
  void resolvesAgainstSameGroundedWorldAndCreatesUserAssumptionShock() throws Exception {
    ScenarioInterpreter interpreter = mock(ScenarioInterpreter.class);
    ScenarioWorldStore worlds = mock(ScenarioWorldStore.class);
    GraphRagClient graphRag = mock(GraphRagClient.class);
    ParallelSimulationService simulations = mock(ParallelSimulationService.class);
    ObjectMapper json = new ObjectMapper();
    ScenarioCandidate extraction = new ScenarioCandidate("v2",
        "NVIDIA production falls by 50%", "NVIDIA", "PRODUCTION", "PERCENT",
        -0.5, null, null, 0.96);
    UUID worldId = UUID.randomUUID(), versionId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID(), scenarioId = UUID.randomUUID();
    var target = new ResolvedTarget(entityId, "COMPANY", "company:nvidia", "NVIDIA");
    var state = json.readTree("""
        {"baselineStatus":"READY","manifest":{"ontologyVersion":"v1","retrievalIndexStatus":"READY","retrievalDocumentCount":1,"expectedRetrievalDocumentCount":1},"companies":[
          {"companyId":"company:nvidia","industry":"semiconductor",
           "baselineProduction":10,"capacity":12,"inventory":2,"demand":10,
           "unitPrice":4,"unitVariableCost":2,"workforce":3,"wagePerWorker":1,
           "productivity":1,"provenance":{"capacity":"evidence-1"}}],"supplyLinks":[]}
        """);
    var world = new WorldVersion(worldId, versionId, "grounded", state);
    when(interpreter.interpret(anyString())).thenReturn(extraction);
    when(worlds.latestReadyVersion()).thenReturn(world);
    when(worlds.resolve(world, "NVIDIA")).thenReturn(target);
    when(graphRag.query(versionId, extraction.target() + " " + extraction.originalQuery(),
        "Bearer token"))
        .thenReturn(json.readTree("{\"worldVersionId\":\"" + versionId + "\",\"paths\":[]}"));
    var parallel = new ParallelResult(scenarioId, worldId, versionId, List.of());
    when(simulations.execute(eq(versionId), anyString(), anyLong(), eq(3), anyList()))
        .thenReturn(parallel);

    ScenarioWorkflowResult result = new NaturalLanguageScenarioService(
        interpreter, worlds, graphRag, simulations, json)
        .execute(extraction.originalQuery(), "Bearer token");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.worldVersionId()).isEqualTo(versionId);
    assertThat(result.shock().basisType()).isEqualTo("USER_ASSUMPTION");
    ArgumentCaptor<List<BranchInput>> branches = ArgumentCaptor.forClass(List.class);
    verify(simulations).execute(eq(versionId), startsWith("Grounded user scenario "), anyLong(), eq(3),
        branches.capture());
    assertThat(branches.getValue()).extracting(BranchInput::name)
        .containsExactly("A: requested shock", "B: policy not numerically applied",
            "C: observed alternative supply");
    assertThat(branches.getValue().get(1).input().path("shocks").get(0)
        .path("capacityMultiplier").asDouble()).isEqualTo(0.5);
  }

  @Test
  void returnsGraphEvidenceButBlocksNumbersWhenBaselineCoverageIsMissing() {
    ScenarioInterpreter interpreter = mock(ScenarioInterpreter.class);
    ScenarioWorldStore worlds = mock(ScenarioWorldStore.class);
    GraphRagClient graphRag = mock(GraphRagClient.class);
    ParallelSimulationService simulations = mock(ParallelSimulationService.class);
    ObjectMapper json = new ObjectMapper();
    ScenarioCandidate extraction = new ScenarioCandidate("v2", "Taiwan supply -50%",
        "Taiwan", "SUPPLY", "PERCENT", -0.5, null, null, 0.9);
    UUID worldId = UUID.randomUUID(), versionId = UUID.randomUUID(), entityId = UUID.randomUUID();
    var world = new WorldVersion(worldId, versionId, "external",
        json.createObjectNode().put("baselineStatus", "INSUFFICIENT_DATA")
            .set("companies", json.createArrayNode()));
    var target = new ResolvedTarget(entityId, "COUNTRY", "country:TW", "Taiwan");
    when(interpreter.interpret(anyString())).thenReturn(extraction);
    when(worlds.latestReadyVersion()).thenReturn(world);
    when(worlds.resolve(world, "Taiwan")).thenReturn(target);
    when(graphRag.query(eq(versionId), anyString(), anyString()))
        .thenReturn(json.createObjectNode().put("worldVersionId", versionId.toString()));

    ScenarioWorkflowResult result = new NaturalLanguageScenarioService(
        interpreter, worlds, graphRag, simulations, json)
        .execute(extraction.originalQuery(), "Bearer token");

    assertThat(result.status()).isEqualTo("INSUFFICIENT_DATA");
    assertThat(result.simulation()).isNull();
    verifyNoInteractions(simulations);
  }
}

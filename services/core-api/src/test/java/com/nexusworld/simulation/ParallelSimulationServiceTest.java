package com.nexusworld.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.model.SimulationRecords.*;
import com.nexusworld.application.port.IndustrialSimulationClient;
import com.nexusworld.application.port.SimulationStore;
import com.nexusworld.application.simulation.ParallelSimulationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ParallelSimulationServiceTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void forksUseIndependentInputsAndTheSameComparisonSeed() throws Exception {
    SimulationStore store = mock(SimulationStore.class);
    IndustrialSimulationClient engine = mock(IndustrialSimulationClient.class);
    UUID world = UUID.randomUUID(), version = UUID.randomUUID(), scenario = UUID.randomUUID();
    UUID branchA = UUID.randomUUID(), branchB = UUID.randomUUID(), branchC = UUID.randomUUID();
    JsonNode baseline = json.readTree("""
        {"companies":[{"companyId":"c1","industry":"electronics",
          "baselineProduction":10,"capacity":12,"inventory":2,"demand":10,
          "unitPrice":5,"unitVariableCost":2,"workforce":3,"wagePerWorker":1,
          "productivity":1}],"supplyLinks":[]}
        """);
    List<BranchInput> inputs = List.of(
        input("A", "[]"), input("B", "[{\"companyId\":\"c1\",\"turn\":1}]"),
        input("C", "[{\"companyId\":\"c1\",\"turn\":2}]")
    );
    when(store.getWorldVersion(version)).thenReturn(new WorldVersion(world, version, "World", baseline));
    when(store.createParallelSetup(eq(version), eq("Scenario"), eq(inputs), any()))
        .thenReturn(new ParallelSetup(scenario, world, version, List.of(
            new Branch(branchA, "A", inputs.get(0).input()),
            new Branch(branchB, "B", inputs.get(1).input()),
            new Branch(branchC, "C", inputs.get(2).input()))));
    when(store.createRun(any(), anyLong(), eq(3), anyString(), any()))
        .thenAnswer(invocation -> UUID.randomUUID());
    JsonNode response = json.readTree("""
        {"resultHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
         "snapshots":[],"finalState":[],"invariants":{}}
        """);
    when(engine.execute(any())).thenReturn(new EngineResult(response, response.path("resultHash").asText(),
        response.path("finalState"), response.path("invariants"), List.of()));
    ParallelResult expected = new ParallelResult(scenario, world, version, List.of());
    when(store.getParallelResult(scenario)).thenReturn(expected);

    ParallelSimulationService service = new ParallelSimulationService(store, engine, json,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(service.execute(version, "Scenario", 100, 3, inputs)).isEqualTo(expected);

    ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
    verify(engine, times(3)).execute(requests.capture());
    assertThat(requests.getAllValues()).extracting(value -> value.path("seed").asLong())
        .containsExactly(100L, 100L, 100L);
    assertThat(requests.getAllValues()).extracting(value -> value.path("shocks").size())
        .containsExactly(0, 1, 1);
    assertThat(baseline.has("shocks")).isFalse();
    verify(store, times(3)).completeRun(any(), any(), any());
  }

  private BranchInput input(String name, String shocks) throws Exception {
    var value = json.createObjectNode();
    value.set("shocks", json.readTree(shocks));
    return new BranchInput(name, value);
  }
}

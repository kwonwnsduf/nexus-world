package com.nexusworld.api.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexusworld.application.model.SimulationRecords.*;
import com.nexusworld.application.simulation.ParallelSimulationService;
import com.nexusworld.application.simulation.NaturalLanguageScenarioService;
import com.nexusworld.application.model.ScenarioWorkflowRecords.ScenarioWorkflowResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SimulationController {
  private final ParallelSimulationService service;
  private final ObjectMapper json;
  private final NaturalLanguageScenarioService naturalLanguageScenarios;

  public SimulationController(ParallelSimulationService service, ObjectMapper json,
      NaturalLanguageScenarioService naturalLanguageScenarios) {
    this.service = service;
    this.json = json;
    this.naturalLanguageScenarios = naturalLanguageScenarios;
  }

  @PostMapping("/worlds")
  @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
  public ResponseEntity<WorldVersion> createWorld(@Valid @RequestBody CreateWorldRequest request) {
    WorldVersion created = service.createWorld(request.name(), request.state());
    return ResponseEntity.created(URI.create("/api/v1/world-versions/" + created.versionId()))
        .body(created);
  }

  @PostMapping("/world-versions/{versionId}/parallel-simulations")
  @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
  public ResponseEntity<ParallelResult> execute(@PathVariable UUID versionId,
      @Valid @RequestBody ParallelSimulationRequest request) {
    var inputs = request.branches().stream().map(branch -> {
      if (!branch.shocks().isArray()) throw new IllegalArgumentException("branch shocks must be an array");
      ObjectNode input = json.createObjectNode();
      input.set("shocks", branch.shocks().deepCopy());
      return new BranchInput(branch.name(), input);
    }).toList();
    var traversal = request.graphTraversal();
    ParallelResult result = service.execute(versionId, request.scenarioName(), request.seed(),
        request.turns(), inputs,
        traversal == null ? "WORLD_VERSION" : traversal.mode().name(),
        traversal == null ? 120 : traversal.maxDepth());
    return ResponseEntity.created(URI.create("/api/v1/parallel-simulations/" + result.scenarioId()))
        .body(result);
  }

  @GetMapping("/parallel-simulations/{scenarioId}")
  public ParallelResult get(@PathVariable UUID scenarioId) { return service.get(scenarioId); }

  @PostMapping("/scenario-runs/from-query")
  @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
  public ResponseEntity<ScenarioWorkflowResult> executeFromQuery(
      @Valid @RequestBody NaturalLanguageScenarioRequest request,
      @RequestHeader("Authorization") String authorization) {
    ScenarioWorkflowResult result = naturalLanguageScenarios.execute(request.query(), authorization);
    if (result.simulation() == null) return ResponseEntity.ok(result);
    return ResponseEntity.created(URI.create("/api/v1/parallel-simulations/"
        + result.simulation().scenarioId())).body(result);
  }
}

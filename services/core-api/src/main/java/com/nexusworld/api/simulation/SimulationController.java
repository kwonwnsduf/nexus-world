package com.nexusworld.api.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexusworld.application.model.SimulationRecords.*;
import com.nexusworld.application.simulation.ParallelSimulationService;
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

  public SimulationController(ParallelSimulationService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
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
    ParallelResult result = service.execute(versionId, request.scenarioName(), request.seed(),
        request.turns(), inputs);
    return ResponseEntity.created(URI.create("/api/v1/parallel-simulations/" + result.scenarioId()))
        .body(result);
  }

  @GetMapping("/parallel-simulations/{scenarioId}")
  public ParallelResult get(@PathVariable UUID scenarioId) { return service.get(scenarioId); }
}

package com.nexusworld.application.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexusworld.application.model.ScenarioWorkflowRecords.*;
import com.nexusworld.application.model.SimulationRecords.BranchInput;
import com.nexusworld.application.port.GraphRagClient;
import com.nexusworld.application.port.ScenarioInterpreter;
import com.nexusworld.application.port.ScenarioWorldStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NaturalLanguageScenarioService {
  private static final Set<String> METRICS =
      Set.of("SUPPLY", "PRODUCTION", "CAPACITY", "DEMAND", "LOGISTICS");
  private final ScenarioInterpreter interpreter;
  private final ScenarioWorldStore worlds;
  private final GraphRagClient graphRag;
  private final ParallelSimulationService simulations;
  private final ObjectMapper json;

  public NaturalLanguageScenarioService(ScenarioInterpreter interpreter,
      ScenarioWorldStore worlds, GraphRagClient graphRag,
      ParallelSimulationService simulations, ObjectMapper json) {
    this.interpreter = interpreter;
    this.worlds = worlds;
    this.graphRag = graphRag;
    this.simulations = simulations;
    this.json = json;
  }

  public ScenarioWorkflowResult execute(String query, String authorization) {
    ScenarioCandidate extraction = interpreter.interpret(query.trim());
    validate(extraction);
    var world = worlds.latestReadyVersion();
    ResolvedTarget target = worlds.resolve(world, extraction.target());
    JsonNode grounded = graphRag.query(world.versionId(),
        extraction.target() + " " + extraction.originalQuery(), authorization);
    ShockDefinition shock = new ShockDefinition(target.entityId(), target.naturalKey(),
        extraction.metric(), extraction.change(), extraction.duration(), "USER_ASSUMPTION");
    ObjectNode quality = quality(world.state(), target, extraction.metric());
    JsonNode manifest = world.state().path("manifest").deepCopy();
    JsonNode provenance = provenance(world.state(), target, extraction.metric());
    if (!quality.path("ready").asBoolean()) {
      return new ScenarioWorkflowResult("v2", "INSUFFICIENT_DATA", world.versionId(),
          extraction, target, shock, manifest, provenance, grounded, quality, null);
    }

    boolean relationshipGraph = "relationship-graph-v1".equals(
        world.state().path("simulationModel").asText());
    JsonNode shockInput = shock(target.naturalKey(), extraction.metric(), extraction.change(),
        extraction.duration(), relationshipGraph);
    List<BranchInput> branches = List.of(
        branch("A: requested shock", shockInput, null, "NONE"),
        branch("B: policy not numerically applied", shockInput,
            extraction.optionalPolicy(), "NONE"),
        branch("C: observed alternative supply", shockInput,
            null, relationshipGraph
                ? "OBSERVED_ALTERNATIVE_SUPPLY" : "NONE"));
    // Scenario names are unique per world in the immutable store. A request identifier keeps
    // repeated runs valid without changing the deterministic seed or numerical request.
    String scenarioName = "Grounded user scenario " + UUID.randomUUID();
    var result = simulations.execute(world.versionId(), scenarioName,
        seed(extraction.originalQuery() + world.versionId()),
        extraction.duration() == null ? 3 : extraction.duration(), branches);
    return new ScenarioWorkflowResult("v2", "COMPLETED", world.versionId(), extraction,
        target, shock, manifest, provenance, grounded, quality, result);
  }

  private ObjectNode quality(JsonNode state, ResolvedTarget target, String metric) {
    ObjectNode result = json.createObjectNode();
    boolean relationshipGraph = "relationship-graph-v1".equals(
        state.path("simulationModel").asText());
    JsonNode companies = relationshipGraph ? state.path("nodes") : state.path("companies");
    JsonNode matched = null;
    if (companies.isArray()) {
      for (JsonNode company : companies) {
        String idField = relationshipGraph ? "entityId" : "companyId";
        if (target.naturalKey().equals(company.path(idField).asText())) matched = company;
      }
    }
    boolean hasManifest = state.path("manifest").isObject();
    boolean retrievalReady = hasManifest
        && "READY".equals(state.path("manifest").path("retrievalIndexStatus").asText())
        && state.path("manifest").path("retrievalDocumentCount").asInt(-1)
            == state.path("manifest").path("expectedRetrievalDocumentCount").asInt(-2);
    boolean hasProvenance = matched != null && matched.path("provenance").isObject()
        && (!relationshipGraph || matched.path("provenance").has(metric));
    boolean hasMetric = matched != null && (relationshipGraph
        ? matched.path("metrics").path(metric).isNumber()
        : legacyMetricPresent(matched, metric));
    result.put("ready", matched != null && hasManifest && retrievalReady
        && hasProvenance && hasMetric);
    ArrayNode missing = result.putArray("missing");
    if (matched == null) missing.add("simulation entity state");
    if (!hasManifest) missing.add("world-version manifest");
    if (!retrievalReady) missing.add("retrieval index");
    if (!hasProvenance) missing.add("value-level provenance");
    if (!hasMetric) missing.add("quantitative baseline for metric " + metric);
    result.put("policyApplied", false);
    result.put("message", missing.isEmpty()
        ? "Quantitative baseline is grounded and ready"
        : "Graph evidence is available, but quantitative simulation is blocked");
    return result;
  }

  private ObjectNode provenance(JsonNode state, ResolvedTarget target, String metric) {
    ObjectNode summary = json.createObjectNode();
    summary.put("shock", "USER_ASSUMPTION");
    ArrayNode baseline = summary.putArray("baselineValues");
    for (JsonNode node : state.path("nodes")) {
      if (target.naturalKey().equals(node.path("entityId").asText())) {
        ObjectNode value = baseline.addObject();
        value.put("entityId", target.naturalKey());
        value.put("metric", metric);
        value.set("value", node.path("metrics").path(metric).deepCopy());
        value.set("provenance", node.path("provenance").path(metric).deepCopy());
      }
    }
    ArrayNode relationships = summary.putArray("relationshipParameters");
    for (JsonNode edge : state.path("relationships")) {
      if (target.naturalKey().equals(edge.path("sourceEntityId").asText())
          || target.naturalKey().equals(edge.path("targetEntityId").asText())) {
        ObjectNode value = relationships.addObject();
        value.put("relationshipId", edge.path("relationshipId").asText());
        value.put("relationshipType", edge.path("relationshipType").asText());
        value.set("parameters", edge.path("parameters").deepCopy());
        value.set("provenance", edge.path("provenance").deepCopy());
      }
    }
    return summary;
  }

  private boolean legacyMetricPresent(JsonNode company, String metric) {
    return switch (metric) {
      case "SUPPLY", "LOGISTICS" -> company.path("inventory").isNumber();
      case "PRODUCTION" -> company.path("baselineProduction").isNumber();
      case "CAPACITY" -> company.path("capacity").isNumber();
      case "DEMAND" -> company.path("demand").isNumber();
      default -> false;
    };
  }

  private BranchInput branch(String name, JsonNode shock, String policy, String strategy) {
    ObjectNode input = json.createObjectNode();
    ArrayNode shocks = input.putArray("shocks");
    if (shock != null) shocks.add(shock.deepCopy());
    if (policy != null && !policy.isBlank()) input.put("unappliedPolicy", policy);
    input.put("strategy", strategy);
    return new BranchInput(name, input);
  }

  private JsonNode shock(String companyId, String metric, double change, Integer duration,
      boolean relationshipGraph) {
    ObjectNode value = json.createObjectNode();
    if (relationshipGraph) {
      value.put("entityId", companyId);
      value.put("metric", metric);
      value.put("change", change);
      value.put("turn", 1);
      if (duration != null) value.put("duration", duration);
      value.put("basisType", "USER_ASSUMPTION");
      return value;
    }
    value.put("companyId", companyId);
    value.put("turn", 1);
    double multiplier = 1.0 + change;
    switch (metric) {
      case "SUPPLY", "LOGISTICS" -> value.put("supplyMultiplier", multiplier);
      case "PRODUCTION", "CAPACITY" -> value.put("capacityMultiplier", multiplier);
      case "DEMAND" -> value.put("demandMultiplier", multiplier);
      default -> throw new IllegalArgumentException("Unsupported scenario metric");
    }
    return value;
  }

  private void validate(ScenarioCandidate value) {
    if (!"v2".equals(value.contractVersion()) || !METRICS.contains(value.metric())
        || !"PERCENT".equals(value.changeType()) || value.change() == 0
        || value.change() < -1 || value.change() > 1 || value.confidence() < 0.5) {
      throw new IllegalArgumentException("Structured scenario candidate is outside allowed bounds");
    }
  }

  private long seed(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      long result = 0;
      for (int index = 0; index < 7; index++) result = (result << 8) | (digest[index] & 0xffL);
      return result;
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot derive deterministic scenario seed", exception);
    }
  }
}

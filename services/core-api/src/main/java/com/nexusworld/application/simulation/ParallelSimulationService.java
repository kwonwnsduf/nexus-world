package com.nexusworld.application.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexusworld.application.model.SimulationRecords.*;
import com.nexusworld.application.port.IndustrialSimulationClient;
import com.nexusworld.application.port.SimulationStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ParallelSimulationService {
  private final SimulationStore store;
  private final IndustrialSimulationClient engine;
  private final ObjectMapper json;
  private final Clock clock;
  private final Neo4jSimulationGraphExpander graphExpander;

  @Autowired
  public ParallelSimulationService(SimulationStore store, IndustrialSimulationClient engine,
      ObjectMapper json, Clock clock, Neo4jSimulationGraphExpander graphExpander) {
    this.store = store;
    this.engine = engine;
    this.json = json;
    this.clock = clock;
    this.graphExpander = graphExpander;
  }

  /** Compatibility constructor for isolated service tests and non-Spring callers. */
  public ParallelSimulationService(SimulationStore store, IndustrialSimulationClient engine,
      ObjectMapper json, Clock clock) {
    this(store, engine, json, clock, null);
  }

  public WorldVersion createWorld(String name, JsonNode state) {
    validateBaseline(state);
    return store.createWorld(name.trim(), state.deepCopy(), clock.instant());
  }

  public ParallelResult execute(UUID versionId, String scenarioName, long seed, int turns,
      List<BranchInput> branchInputs) {
    return execute(versionId, scenarioName, seed, turns, branchInputs, "WORLD_VERSION", 120);
  }

  public ParallelResult execute(UUID versionId, String scenarioName, long seed, int turns,
      List<BranchInput> branchInputs, String graphTraversal, int maxDepth) {
    if (turns < 1 || turns > 120) throw new IllegalArgumentException("turns must be between 1 and 120");
    if (branchInputs.size() < 2 || branchInputs.size() > 3) {
      throw new IllegalArgumentException("parallel simulation requires two or three branches");
    }
    if (branchInputs.stream().map(value -> value.name().trim().toLowerCase(Locale.ROOT)).distinct().count()
        != branchInputs.size()) throw new IllegalArgumentException("branch names must be unique");
    WorldVersion baseline = store.getWorldVersion(versionId);
    Set<String> companyIds = validateBaseline(baseline.state());
    boolean relationshipGraph = "relationship-graph-v1".equals(
        baseline.state().path("simulationModel").asText());
    boolean neo4jTraversal = "NEO4J_PATHS".equals(graphTraversal);
    if (!Set.of("WORLD_VERSION", "NEO4J_PATHS").contains(graphTraversal)) {
      throw new IllegalArgumentException("graphTraversal mode is not supported");
    }
    if (neo4jTraversal && !relationshipGraph) {
      throw new IllegalArgumentException("NEO4J_PATHS is only available for relationship graphs");
    }
    if (neo4jTraversal && graphExpander == null) {
      throw new IllegalStateException("Neo4j graph expansion is not configured");
    }
    validateBranches(branchInputs, companyIds, turns, relationshipGraph);
    ParallelSetup setup = store.createParallelSetup(versionId, scenarioName.trim(), branchInputs,
        clock.instant());
    for (int index = 0; index < setup.branches().size(); index++) {
      Branch branch = setup.branches().get(index);
      long branchSeed = seed;
      ObjectNode request = json.createObjectNode();
      request.put("contractVersion", "v1");
      request.put("seed", branchSeed);
      request.put("turns", turns);
      if (relationshipGraph) {
        request.put("simulationModel", "relationship-graph-v1");
        if (neo4jTraversal) {
          var expanded = graphExpander.expand(versionId, baseline.state(),
              branch.input().path("shocks"), maxDepth);
          request.set("nodes", expanded.nodes());
          request.set("relationships", expanded.relationships());
          request.put("traversalMode", "NEO4J_PATHS");
          request.put("maxPropagationDepth", expanded.maxDepth());
        } else {
          request.set("nodes", baseline.state().get("nodes").deepCopy());
          request.set("relationships", baseline.state().get("relationships").deepCopy());
          request.put("traversalMode", "WORLD_VERSION");
          request.put("maxPropagationDepth", Math.min(120, Math.max(1, maxDepth)));
        }
        request.put("strategy", branch.input().path("strategy").asText("NONE"));
      } else {
        request.set("companies", baseline.state().get("companies").deepCopy());
        request.set("supplyLinks", baseline.state().get("supplyLinks").deepCopy());
      }
      request.set("shocks", branch.input().path("shocks").deepCopy());
      String requestHash = sha256(request);
      UUID runId = store.createRun(branch.id(), branchSeed, turns, requestHash, clock.instant());
      try {
        EngineResult result = engine.execute(request);
        store.completeRun(runId, result, clock.instant());
      } catch (RuntimeException exception) {
        store.failRun(runId, safeMessage(exception), clock.instant());
        throw new SimulationExecutionException("Simulation engine failed for branch " + branch.name(), exception);
      }
    }
    return store.getParallelResult(setup.scenarioId());
  }

  public ParallelResult get(UUID scenarioId) { return store.getParallelResult(scenarioId); }

  private Set<String> validateBaseline(JsonNode state) {
    if (state != null && "relationship-graph-v1".equals(
        state.path("simulationModel").asText())) return validateRelationshipGraph(state);
    if (state == null || !state.isObject() || !state.path("companies").isArray()
        || state.path("companies").isEmpty() || !state.path("supplyLinks").isArray()) {
      throw new IllegalArgumentException("state must contain non-empty companies and supplyLinks arrays");
    }
    if (state.path("companies").size() > 5000 || state.path("supplyLinks").size() > 20000) {
      throw new IllegalArgumentException("baseline exceeds company or supply-link limit");
    }
    Set<String> companyIds = new HashSet<>();
    for (JsonNode company : state.path("companies")) {
      String id = requiredText(company, "companyId");
      requiredText(company, "industry");
      if (!companyIds.add(id)) throw new IllegalArgumentException("companyId values must be unique");
      nonNegative(company, "baselineProduction", false);
      nonNegative(company, "capacity", false);
      nonNegative(company, "inventory", false);
      nonNegative(company, "demand", false);
      nonNegative(company, "unitPrice", true);
      nonNegative(company, "unitVariableCost", false);
      nonNegative(company, "workforce", false);
      nonNegative(company, "wagePerWorker", false);
      nonNegative(company, "productivity", true);
    }
    Set<String> links = new HashSet<>();
    for (JsonNode link : state.path("supplyLinks")) {
      String supplier = requiredText(link, "supplierId");
      String customer = requiredText(link, "customerId");
      if (!companyIds.contains(supplier) || !companyIds.contains(customer)) {
        throw new IllegalArgumentException("supply links must reference known companies");
      }
      if (supplier.equals(customer)) throw new IllegalArgumentException("self-referencing supply links are not allowed");
      if (!links.add(supplier + "\u0000" + customer)) {
        throw new IllegalArgumentException("duplicate supply links are not allowed");
      }
      nonNegative(link, "inputUnitsPerOutput", true);
      nonNegative(link, "maxFlow", false);
      nonNegative(link, "unitCost", false);
    }
    return Set.copyOf(companyIds);
  }

  private Set<String> validateRelationshipGraph(JsonNode state) {
    if (!state.path("nodes").isArray() || state.path("nodes").isEmpty()
        || !state.path("relationships").isArray() || !state.path("manifest").isObject()) {
      throw new IllegalArgumentException(
          "relationship graph requires nodes, relationships, and a version manifest");
    }
    Set<String> ids = new HashSet<>();
    for (JsonNode node : state.path("nodes")) {
      String id = requiredText(node, "entityId");
      requiredText(node, "entityType");
      if (!node.path("metrics").isObject() || !node.path("provenance").isObject()) {
        throw new IllegalArgumentException("graph node metrics require value-level provenance");
      }
      if (!ids.add(id)) throw new IllegalArgumentException("graph entity ids must be unique");
    }
    for (JsonNode edge : state.path("relationships")) {
      String source = requiredText(edge, "sourceEntityId");
      String target = requiredText(edge, "targetEntityId");
      String type = requiredText(edge, "relationshipType");
      if (!ids.contains(source) || !ids.contains(target) || source.equals(target)) {
        throw new IllegalArgumentException("graph relationship endpoints are invalid");
      }
      if (!Set.of("TRADE_FLOW", "SUPPLIES", "DEPENDS_ON", "PRODUCES", "SHIPS_VIA")
          .contains(type)) {
        throw new IllegalArgumentException("unsupported quantitative relationship type " + type);
      }
      if (!edge.path("parameters").has("dependencyRatio")) {
        throw new IllegalArgumentException("quantitative relationship is missing dependencyRatio");
      }
      bounded(edge.path("parameters"), "dependencyRatio", 0, 1);
      if (!edge.path("provenance").isObject()) {
        throw new IllegalArgumentException("graph relationship parameters require provenance");
      }
    }
    return Set.copyOf(ids);
  }

  private void validateBranches(List<BranchInput> branches, Set<String> companyIds, int turns,
      boolean relationshipGraph) {
    for (BranchInput branch : branches) {
      JsonNode shocks = branch.input().path("shocks");
      if (!shocks.isArray() || shocks.size() > 20000) {
        throw new IllegalArgumentException("branch shocks must be an array with at most 20000 entries");
      }
      Set<String> keys = new HashSet<>();
      for (JsonNode shock : shocks) {
        String companyId = requiredText(shock, relationshipGraph ? "entityId" : "companyId");
        int turn = shock.path("turn").asInt(0);
        if (!companyIds.contains(companyId)) throw new IllegalArgumentException("shocks must reference known companies");
        if (turn < 1 || turn > turns) throw new IllegalArgumentException("shock turn must be within the run horizon");
        if (!keys.add(companyId + "\u0000" + turn)) {
          throw new IllegalArgumentException("duplicate company shock in the same turn is not allowed");
        }
        if (relationshipGraph) {
          requiredText(shock, "metric");
          bounded(shock, "change", -1, 1);
          if (!"USER_ASSUMPTION".equals(shock.path("basisType").asText())) {
            throw new IllegalArgumentException("graph shock basis must be USER_ASSUMPTION");
          }
        } else {
          bounded(shock, "supplyMultiplier", 0, 2);
          bounded(shock, "demandMultiplier", 0, 2);
          bounded(shock, "capacityMultiplier", 0, 2);
          bounded(shock, "priceMultiplier", 0.25, 4);
        }
      }
    }
  }

  private String requiredText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException(field + " must be a non-blank string");
    }
    return value.asText();
  }

  private void nonNegative(JsonNode node, String field, boolean exclusive) {
    JsonNode value = node.path(field);
    if (!value.isNumber() || !Double.isFinite(value.asDouble())
        || (exclusive ? value.asDouble() <= 0 : value.asDouble() < 0)) {
      throw new IllegalArgumentException(field + (exclusive ? " must be positive" : " must be non-negative"));
    }
  }

  private void bounded(JsonNode node, String field, double minimum, double maximum) {
    if (!node.has(field)) return;
    JsonNode value = node.path(field);
    if (!value.isNumber() || !Double.isFinite(value.asDouble())
        || value.asDouble() < minimum || value.asDouble() > maximum) {
      throw new IllegalArgumentException(field + " is outside its allowed range");
    }
  }

  private String sha256(JsonNode value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot hash simulation request", exception);
    }
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
    return message.substring(0, Math.min(message.length(), 1000));
  }
}

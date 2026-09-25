package com.nexusworld.infrastructure.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.model.SimulationRecords.*;
import com.nexusworld.application.port.SimulationStore;
import com.nexusworld.application.simulation.SimulationNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSimulationStore implements SimulationStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcSimulationStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

  @Override @Transactional
  public WorldVersion createWorld(String name, JsonNode state, Instant now) {
    UUID worldId = UUID.randomUUID(), versionId = UUID.randomUUID();
    jdbc.update("INSERT INTO worlds(id,name,status,created_at,updated_at) VALUES (?,?,'ACTIVE',?,?)",
        worldId, name, timestamp(now), timestamp(now));
    jdbc.update("INSERT INTO world_versions(id,world_id,version_number,state,created_at) VALUES (?,?,1,?::jsonb,?)",
        versionId, worldId, state.toString(), timestamp(now));
    return new WorldVersion(worldId, versionId, name, state.deepCopy());
  }

  @Override @Transactional
  public WorldVersion createNextWorldVersion(String worldName, JsonNode state, Instant now) {
    List<UUID> existing = jdbc.query("SELECT id FROM worlds WHERE name=? ORDER BY created_at LIMIT 1 FOR UPDATE",
        (rs, row) -> rs.getObject(1, UUID.class), worldName);
    if (existing.isEmpty()) return createWorld(worldName, state, now);
    UUID worldId = existing.get(0);
    Integer next = jdbc.queryForObject(
        "SELECT COALESCE(MAX(version_number),0)+1 FROM world_versions WHERE world_id=?",
        Integer.class, worldId);
    UUID versionId = UUID.randomUUID();
    jdbc.update("INSERT INTO world_versions(id,world_id,version_number,state,created_at) VALUES (?,?,?,?::jsonb,?)",
        versionId, worldId, next, state.toString(), timestamp(now));
    jdbc.update("UPDATE worlds SET updated_at=? WHERE id=?", timestamp(now), worldId);
    return new WorldVersion(worldId, versionId, worldName, state.deepCopy());
  }

  @Override
  public boolean hasWorldSnapshot(String worldName, String snapshotFingerprint) {
    Integer count = jdbc.queryForObject("""
        SELECT count(*) FROM world_versions v JOIN worlds w ON w.id=v.world_id
        WHERE w.name=? AND v.state->'manifest'->>'snapshotFingerprint'=?
          AND v.state->'manifest'->>'retrievalIndexStatus'='READY'
          AND v.state->'manifest'->'retrievalDocumentCount' IS NOT NULL
          AND v.state->'manifest'->>'createdAt' IS NOT NULL
        """, Integer.class, worldName, snapshotFingerprint);
    return count != null && count > 0;
  }

  @Override
  public WorldVersion getWorldVersion(UUID versionId) {
    List<WorldVersion> found = jdbc.query("SELECT w.id,w.name,v.state FROM world_versions v JOIN worlds w ON w.id=v.world_id WHERE v.id=?",
        (rs, row) -> new WorldVersion(rs.getObject(1, UUID.class), versionId, rs.getString(2), read(rs.getString(3))), versionId);
    if (found.isEmpty()) throw new SimulationNotFoundException("World version not found");
    return found.get(0);
  }

  @Override @Transactional
  public ParallelSetup createParallelSetup(UUID versionId, String scenarioName,
      List<BranchInput> inputs, Instant now) {
    WorldVersion world = getWorldVersion(versionId);
    UUID scenarioId = UUID.randomUUID();
    jdbc.update("INSERT INTO scenarios(id,world_id,name,created_at,updated_at) VALUES (?,?,?,?,?)",
        scenarioId, world.worldId(), scenarioName, timestamp(now), timestamp(now));
    List<Branch> branches = new ArrayList<>();
    for (BranchInput input : inputs) {
      UUID id = UUID.randomUUID();
      jdbc.update("INSERT INTO scenario_branches(id,scenario_id,baseline_world_version_id,name,status,branch_input,created_at,updated_at) VALUES (?,?,?,?,'READY',?::jsonb,?,?)",
          id, scenarioId, versionId, input.name().trim(), input.input().toString(),
          timestamp(now), timestamp(now));
      branches.add(new Branch(id, input.name().trim(), input.input().deepCopy()));
    }
    return new ParallelSetup(scenarioId, world.worldId(), versionId, List.copyOf(branches));
  }

  @Override @Transactional
  public UUID createRun(UUID branchId, long seed, int turns, String requestHash, Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.update("UPDATE scenario_branches SET status='RUNNING',updated_at=? WHERE id=?",
        timestamp(now), branchId);
    jdbc.update("INSERT INTO simulation_runs(id,scenario_branch_id,seed,status,turns,request_hash,requested_at,started_at) VALUES (?,?,?,'RUNNING',?,?,?,?)",
        id, branchId, seed, turns, requestHash, timestamp(now), timestamp(now));
    return id;
  }

  @Override @Transactional
  public void completeRun(UUID runId, EngineResult result, Instant now) {
    for (JsonNode snapshot : result.snapshots()) {
      jdbc.update("INSERT INTO turn_snapshots(id,simulation_run_id,turn,state,metrics,invariant_violations,created_at) VALUES (?,?,?,?::jsonb,?::jsonb,?::jsonb,?)",
          UUID.randomUUID(), runId, snapshot.path("turn").asInt(), snapshot.toString(),
          snapshot.path("metrics").toString(), snapshot.path("invariantViolations").toString(),
          timestamp(now));
    }
    jdbc.update("UPDATE simulation_runs SET status='COMPLETED',result_hash=?,final_state=?::jsonb,invariant_results=?::jsonb,completed_at=? WHERE id=?",
        result.resultHash(), result.finalState().toString(), result.invariants().toString(),
        timestamp(now), runId);
    jdbc.update("UPDATE scenario_branches SET status='COMPLETED',updated_at=? WHERE id=(SELECT scenario_branch_id FROM simulation_runs WHERE id=?)",
        timestamp(now), runId);
  }

  @Override @Transactional
  public void failRun(UUID runId, String reason, Instant now) {
    jdbc.update("UPDATE simulation_runs SET status='FAILED',failure_reason=?,completed_at=? WHERE id=?",
        reason, timestamp(now), runId);
    jdbc.update("UPDATE scenario_branches SET status='FAILED',updated_at=? WHERE id=(SELECT scenario_branch_id FROM simulation_runs WHERE id=?)",
        timestamp(now), runId);
  }

  @Override
  public ParallelResult getParallelResult(UUID scenarioId) {
    List<ParallelResult> setup = jdbc.query("SELECT s.world_id,b.baseline_world_version_id FROM scenarios s JOIN scenario_branches b ON b.scenario_id=s.id WHERE s.id=? ORDER BY b.created_at LIMIT 1",
        (rs, row) -> new ParallelResult(scenarioId, rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), List.of()), scenarioId);
    if (setup.isEmpty()) throw new SimulationNotFoundException("Parallel simulation not found");
    List<Run> baseRuns = jdbc.query("SELECT r.id,b.id,b.name,r.seed,r.turns,r.status,r.result_hash,r.final_state,r.invariant_results,r.failure_reason,r.requested_at,r.completed_at FROM scenario_branches b LEFT JOIN simulation_runs r ON r.scenario_branch_id=b.id WHERE b.scenario_id=? ORDER BY b.created_at,r.requested_at",
        (rs, row) -> mapRun(rs), scenarioId);
    List<Run> runs = baseRuns.stream().map(run -> {
      if (run.id() == null) return run;
      List<JsonNode> snapshots = jdbc.query(
          "SELECT state FROM turn_snapshots WHERE simulation_run_id=? ORDER BY turn",
          (rs, row) -> read(rs.getString(1)), run.id());
      return new Run(run.id(), run.branchId(), run.branchName(), run.seed(), run.turns(),
          run.status(), run.resultHash(), run.finalState(), run.invariants(), run.failureReason(),
          run.requestedAt(), run.completedAt(), snapshots);
    }).toList();
    ParallelResult value = setup.get(0);
    return new ParallelResult(scenarioId, value.worldId(), value.worldVersionId(), runs);
  }

  private Run mapRun(ResultSet rs) throws SQLException {
    UUID runId = rs.getObject(1, UUID.class);
    if (runId == null) return new Run(null, rs.getObject(2, UUID.class), rs.getString(3), 0, 0,
        "READY", null, null, null, null, null, null, List.of());
    return new Run(runId, rs.getObject(2, UUID.class), rs.getString(3), rs.getLong(4),
        rs.getInt(5), rs.getString(6), rs.getString(7), readNullable(rs.getString(8)),
        readNullable(rs.getString(9)), rs.getString(10), rs.getTimestamp(11).toInstant(),
        rs.getTimestamp(12) == null ? null : rs.getTimestamp(12).toInstant(), List.of());
  }

  private JsonNode readNullable(String value) { return value == null ? null : read(value); }
  private Timestamp timestamp(Instant value) { return Timestamp.from(value); }
  private JsonNode read(String value) {
    try { return json.readTree(value); }
    catch (Exception exception) { throw new IllegalStateException("Invalid JSON persisted in simulation store", exception); }
  }
}

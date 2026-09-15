package com.nexusworld.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusworld.domain.id.WorldId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseMigrationIntegrationTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("nexusworld")
          .withUsername("nexusworld")
          .withPassword("integration-only");

  @Test
  @Order(1)
  void migrationIsRepeatableAndUuidDomainIdsRoundTrip() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load();

    MigrateResult firstRun = flyway.migrate();
    MigrateResult secondRun = flyway.migrate();

    assertThat(firstRun.migrationsExecuted).isEqualTo(8);
    assertThat(secondRun.migrationsExecuted).isZero();
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

    WorldId id = WorldId.generate();
    try (Connection connection = POSTGRES.createConnection("");
        PreparedStatement insert =
            connection.prepareStatement("INSERT INTO worlds (id, name) VALUES (?, ?)")) {
      insert.setObject(1, id.value());
      insert.setString(2, "Integration world");
      assertThat(insert.executeUpdate()).isEqualTo(1);
    }

    try (Connection connection = POSTGRES.createConnection("");
        PreparedStatement query =
            connection.prepareStatement("SELECT id FROM worlds WHERE id = ?")) {
      query.setObject(1, id.value());
      try (ResultSet result = query.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(WorldId.parse(result.getObject("id", UUID.class).toString())).isEqualTo(id);
      }
    }
  }

  @Test
  @Order(2)
  void provenanceConstraintsRejectInvalidConfidenceAndAmbiguousOrigins() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();

    UUID actorId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();
    UUID assumptionId = UUID.randomUUID();
    try (Connection connection = POSTGRES.createConnection("")) {
      execute(
          connection,
          "INSERT INTO users (id, username, normalized_username, password_hash) VALUES (?, ?, ?,"
              + " ?)",
          actorId,
          "constraint-user-" + actorId,
          "constraint-user-" + actorId,
          "not-used-in-this-test");
      execute(
          connection,
          "INSERT INTO data_sources (id, source_key, source_type, title, retrieved_at, created_by)"
              + " VALUES (?, ?, 'DATASET', ?, CURRENT_TIMESTAMP, ?)",
          sourceId,
          "constraint-source-" + sourceId,
          "Constraint source",
          actorId);
      execute(
          connection,
          "INSERT INTO evidence_items (id, source_id, evidence_type, claim_text, measured_value,"
              + " confidence, created_by) VALUES (?, ?, 'MEASUREMENT', ?, '1'::jsonb, 1, ?)",
          evidenceId,
          sourceId,
          "Measured fact",
          actorId);
      execute(
          connection,
          "INSERT INTO assumptions (id, assumption_key, category, statement, rationale, confidence,"
              + " created_by) VALUES (?, ?, 'TEST', ?, ?, 0.5, ?)",
          assumptionId,
          "constraint-assumption-" + assumptionId,
          "Assumed fact",
          "Constraint test",
          actorId);

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO evidence_items (id, source_id, evidence_type, claim_text,"
                          + " measured_value, confidence, created_by) VALUES (?, ?, 'MEASUREMENT',"
                          + " ?, '1'::jsonb, 1.001, ?)",
                      UUID.randomUUID(),
                      sourceId,
                      "Invalid confidence",
                      actorId))
          .hasMessageContaining("evidence_items_confidence_range");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO provenance_links (id, subject_type, subject_id, property_path,"
                          + " evidence_id, assumption_id, created_by) VALUES (?, 'WORLD_VERSION',"
                          + " ?, '/metric', ?, ?, ?)",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      evidenceId,
                      assumptionId,
                      actorId))
          .hasMessageContaining("provenance_links_exactly_one_origin");
    }
  }

  @Test
  @Order(3)
  void ontologyConstraintsRejectInvalidEndpointTypesAndCrossWorldEdges() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    UUID actor = UUID.randomUUID(),
        worldA = UUID.randomUUID(),
        worldB = UUID.randomUUID(),
        versionA = UUID.randomUUID(),
        versionB = UUID.randomUUID();
    UUID company = UUID.randomUUID(), cohort = UUID.randomUUID(), foreignBank = UUID.randomUUID();
    try (Connection connection = POSTGRES.createConnection("")) {
      execute(
          connection,
          "INSERT INTO users (id,username,normalized_username,password_hash) VALUES (?,?,?,?)",
          actor,
          "ontology-" + actor,
          "ontology-" + actor,
          "unused");
      execute(connection, "INSERT INTO worlds (id,name) VALUES (?,?)", worldA, "World A");
      execute(connection, "INSERT INTO worlds (id,name) VALUES (?,?)", worldB, "World B");
      execute(
          connection,
          "INSERT INTO world_versions (id,world_id,version_number) VALUES (?,?,1)",
          versionA,
          worldA);
      execute(
          connection,
          "INSERT INTO world_versions (id,world_id,version_number) VALUES (?,?,1)",
          versionB,
          worldB);
      execute(
          connection,
          "INSERT INTO world_graph_entities"
              + " (id,world_version_id,entity_type,natural_key,display_name,attributes,created_by)"
              + " VALUES (?,?, 'COMPANY','company:test','Test"
              + " Company','{\"jurisdiction\":\"KR\",\"industryCode\":\"C26\"}'::jsonb,?)",
          company,
          versionA,
          actor);
      execute(
          connection,
          "INSERT INTO world_graph_entities"
              + " (id,world_version_id,entity_type,natural_key,display_name,attributes,created_by)"
              + " VALUES (?,?, 'DEMOGRAPHIC_COHORT','cohort:test','Worker"
              + " cohort','{\"populationModelId\":\"kr-2025\",\"baseYear\":2025,\"weight\":1000}'::jsonb,?)",
          cohort,
          versionA,
          actor);
      execute(
          connection,
          "INSERT INTO world_graph_entities"
              + " (id,world_version_id,entity_type,natural_key,display_name,attributes,created_by)"
              + " VALUES (?,?, 'BANK','bank:test','Foreign"
              + " bank','{\"jurisdiction\":\"US\"}'::jsonb,?)",
          foreignBank,
          versionB,
          actor);
      execute(
          connection,
          "INSERT INTO world_graph_relationships"
              + " (id,world_version_id,relationship_type,source_entity_id,"
              + "source_entity_type,target_entity_id,target_entity_type,created_by)"
              + " VALUES (?,?, 'EMPLOYS',?,'COMPANY',?,'DEMOGRAPHIC_COHORT',?)",
          UUID.randomUUID(),
          versionA,
          company,
          cohort,
          actor);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO world_graph_relationships"
                          + " (id,world_version_id,relationship_type,source_entity_id,"
                          + "source_entity_type,target_entity_id,target_entity_type,created_by)"
                          + " VALUES (?,?, 'LENDS_TO',?,'COMPANY',?,'DEMOGRAPHIC_COHORT',?)",
                      UUID.randomUUID(),
                      versionA,
                      company,
                      cohort,
                      actor))
          .hasMessageContaining("world_graph_relationship_definition_fk");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO world_graph_relationships"
                          + " (id,world_version_id,relationship_type,source_entity_id,"
                          + "source_entity_type,target_entity_id,target_entity_type,created_by)"
                          + " VALUES (?,?, 'LENDS_TO',?,'BANK',?,'COMPANY',?)",
                      UUID.randomUUID(),
                      versionA,
                      foreignBank,
                      company,
                      actor))
          .hasMessageContaining("world_graph_relationship_source_fk");
    }
  }

  @Test
  @Order(4)
  void ontologyPropertyAndActionCatalogConstraintsAreEnforced() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();

    try (Connection connection = POSTGRES.createConnection("")) {
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO ontology_property_types "
                          + "(entity_type,code,display_name,description,data_type) "
                          + "VALUES ('COMPANY','invalid','Invalid','Invalid type','MONEY')"))
          .hasMessageContaining("ontology_property_data_type_valid");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO ontology_action_types "
                          + "(code,actor_type,target_type,description,parameters_schema) "
                          + "VALUES ('INVALID_SCHEMA','COMPANY','FACILITY','Invalid','[]'::jsonb)"))
          .hasMessageContaining("ontology_action_parameters_object");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO ontology_action_types "
                          + "(code,actor_type,target_type,description) "
                          + "VALUES ('INVALID_ACTOR','UNKNOWN','FACILITY','Invalid')"))
          .hasMessageContaining("ontology_action_types_actor_type_fkey");
    }
  }

  private void execute(Connection connection, String sql, Object... values) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < values.length; index++) {
        statement.setObject(index + 1, values[index]);
      }
      statement.executeUpdate();
    }
  }
}

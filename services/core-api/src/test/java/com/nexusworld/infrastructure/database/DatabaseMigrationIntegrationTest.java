package com.nexusworld.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexusworld.domain.id.WorldId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("nexusworld")
                    .withUsername("nexusworld")
                    .withPassword("integration-only");

    @Test
    void migrationIsRepeatableAndUuidDomainIdsRoundTrip() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();

        MigrateResult firstRun = flyway.migrate();
        MigrateResult secondRun = flyway.migrate();

        assertThat(firstRun.migrationsExecuted).isEqualTo(3);
        assertThat(secondRun.migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        WorldId id = WorldId.generate();
        try (Connection connection = POSTGRES.createConnection("");
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO worlds (id, name) VALUES (?, ?)")) {
            insert.setObject(1, id.value());
            insert.setString(2, "Integration world");
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }

        try (Connection connection = POSTGRES.createConnection("");
                PreparedStatement query = connection.prepareStatement(
                        "SELECT id FROM worlds WHERE id = ?")) {
            query.setObject(1, id.value());
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(WorldId.parse(result.getObject("id", UUID.class).toString())).isEqualTo(id);
            }
        }
    }
}

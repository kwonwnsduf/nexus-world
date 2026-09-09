# Day 3 — PostgreSQL, Flyway, and domain identifiers

## Goal

Establish Core API's transactional persistence boundary with a forward-only PostgreSQL schema and strongly typed identifiers. Prove that the schema can be migrated on a clean database and safely re-checked on an already migrated database.

## Completed

- Added Spring JDBC, PostgreSQL, and Flyway runtime integration to Core API.
- Added startup configuration through `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `DATABASE_POOL_SIZE`.
- Connected Core API to the Compose PostgreSQL service and made startup wait for database readiness.
- Added the first migration for `worlds`, `world_versions`, `scenarios`, `scenario_branches`, and `simulation_runs`.
- Added relational constraints, lifecycle checks, foreign keys, uniqueness rules, and lookup indexes.
- Added strongly typed Java UUID records for each Day 3 domain identifier.
- Added a real PostgreSQL integration test that runs Flyway twice, validates the schema, and round-trips a typed ID.
- Extended smoke checks to verify Core API's database health component.

## Migration rules

- Applied migrations are immutable. Schema changes use a new `V<N>__description.sql` file.
- Core API generates IDs; PostgreSQL stores them in native `UUID` columns.
- Flyway validation runs at application startup and destructive `clean` is disabled in runtime configuration.
- Tables scheduled for later days—evidence, population, outbox, audit, and authorization—are intentionally not pre-created.

## Verification

The PostgreSQL integration test uses Testcontainers and is skipped only when a Docker runtime is unavailable. Compose smoke testing independently verifies that the packaged Core API migrates and connects to PostgreSQL.

## Deferred

Repository behavior and public world/scenario APIs, authentication ownership, evidence provenance, simulation state, population data, and event/outbox tables remain assigned to their later days.

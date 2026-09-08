# API and event contracts v0.1

- Public HTTP APIs use `/api/v1` and additive evolution within a major version.
- Health endpoints are not business APIs and remain `/api/health`, `/actuator/health`, and `/health`.
- Events use `<domain>.<past-tense-action>.v1` as schema identifiers and carry `eventId`, `occurredAt`, `traceparent`, `worldId`, and relevant version references.
- Kafka topic families are `simulation.commands.v1`, `simulation.events.v1`, `ingestion.commands.v1`, `ingestion.events.v1`, `crisis.events.v1`, `incident.events.v1`, and `notification.commands.v1`.
- Large household states are referenced by snapshot ID; events contain summaries, not row-level payloads.

Contract changes are committed under `contracts/` and validated before dependent implementation changes.


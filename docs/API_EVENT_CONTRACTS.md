# API and event contracts v0.1

- Public HTTP APIs use `/api/v1` and additive evolution within a major version.
- Health endpoints are not business APIs and remain `/api/health`, `/actuator/health`, and `/health`.
- Events use `<domain>.<past-tense-action>.v1` as schema identifiers and carry `eventId`, `occurredAt`, `traceparent`, `worldId`, and relevant version references.
- Kafka topic families are `simulation.commands.v1`, `simulation.events.v1`, `ingestion.commands.v1`, `ingestion.events.v1`, `crisis.events.v1`, `incident.events.v1`, and `notification.commands.v1`.
- Large household states are referenced by snapshot ID; events contain summaries, not row-level payloads.

Contract changes are committed under `contracts/` and validated before dependent implementation changes.

## Ontology v1

`GET /api/v1/ontology` returns Entity, Property, Relationship, and Action Type catalogs. Property definitions drive
validation of values stored in graph-entity `attributes` JSONB. `POST /api/v1/ontology/actions/validate` checks an
Action's actor/target types and parameters without persisting or executing the Action. World graph creation and reads
remain under `/api/v1/world-versions/{versionId}/graph`.

## Provenance v1

Day 6 adds append-oriented `/api/v1/sources`, `/api/v1/evidence`, `/api/v1/assumptions`, and
`/api/v1/provenance-links` contracts. Writes require analyst-or-higher authority; authenticated viewers may resolve a
provenance chain. Each link addresses a domain property with a JSON Pointer and has exactly one evidence or assumption
origin. Payload definitions are versioned in `contracts/schemas/provenance-contract-v1.json`.

## Retrieval v1

Day 15-17 adds AI-service-owned `/api/v1/retrieval/documents`, `/search`, and `/answer` contracts. Documents are
split on Markdown section boundaries and retain character locators. Search explicitly selects `keyword`, `vector`, or
`hybrid`; hybrid uses reciprocal-rank fusion followed by a deterministic lexical/section reranker. Answers are
extractive and every statement marker resolves to returned chunk metadata. The service does not fabricate an answer
when indexed evidence is absent. Ingestion accepts optional `evidenceId` and `dataSourceId`; chunk and citation payloads
return both nullable IDs and continue returning `sourceUri`. Search accepts additive `rerank` (default `true`) and
reports whether reranking was applied. Payload definitions live in `contracts/schemas/retrieval-contract-v1.json`.

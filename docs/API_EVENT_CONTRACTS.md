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

GraphRAG uses `POST /api/v1/graphrag/query` with a world version and a bounded free-text query. Core API resolves indexed
PostgreSQL exact-alias, trigram, and FTS candidates, then the AI service requests one-to-three-hop Neo4j paths and returns
ranked paths, graph evidence, and Day 17 text citations under `contracts/schemas/graphrag-contract-v1.json`. Its text
evidence stage uses `hybrid` retrieval by default (keyword + embedding candidates with reciprocal-rank fusion and the
deterministic reranker). If the embedding provider is unavailable, GraphRAG falls back to keyword retrieval while the
standalone `/retrieval/search` endpoint still reports embedding errors for explicitly requested vector/hybrid searches.

## Deterministic simulation and parallel worlds v1

The AI service exposes `POST /api/v1/simulations/execute` for bounded deterministic numerical execution. Core API exposes
`POST /api/v1/worlds`, `POST /api/v1/world-versions/{versionId}/parallel-simulations`, and
`GET /api/v1/parallel-simulations/{scenarioId}`. Payload constraints are defined in
`contracts/schemas/simulation-contract-v1.json`. Public callers cannot overwrite a baseline or write a turn snapshot.

Relationship-graph simulations accept optional `graphTraversal`. `WORLD_VERSION` keeps the immutable world snapshot as
the execution topology. `NEO4J_PATHS` resolves each shocked entity to the same-version Neo4j projection, reads bounded
one-to-three-hop paths, and sends only grounded quantitative edges on those paths to the deterministic engine. Each
snapshot returns a traversal trace showing the A→B and B→C relationship calculations. Neo4j topology never supplies a
coefficient by itself: an edge must match a world-version quantitative edge or contain a provenance-bearing
`parameters.dependencyRatio` in its projected attributes.

Phase 1 adds `POST /api/v1/scenario-runs/from-query`. AI uses OpenAI structured output only to extract a target,
metric, percentage change, duration and optional policy. Core resolves the target in the latest data-built immutable
World Version, invokes GraphRAG against that exact version, classifies the shock as a user assumption, and runs the
existing parallel contract only when the quantitative baseline and value-level provenance are complete. Otherwise it
returns `INSUFFICIENT_DATA` with graph paths, citations and missing coverage instead of inventing coefficients. The v2
payload remains in `contracts/schemas/scenario-workflow-contract-v1.json` so the route name stays stable.

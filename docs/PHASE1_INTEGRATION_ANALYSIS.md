# Phase 1 integration analysis

## Existing Day 1-20 capabilities

- Core owns authentication, immutable worlds/world versions, scenarios, branches, runs and snapshots.
- Provenance stores source, evidence, assumptions and links.
- Thirteen external-source adapters persist raw payloads, normalized records, rejections and ingestion runs idempotently.
- The versioned ontology validates graph entities, relationships, properties and actions.
- Exact identifier resolution, PostgreSQL graph search and optional atomic Neo4j projection are implemented.
- AI provides section-aware retrieval, FTS/pgvector hybrid retrieval, reranking and bounded GraphRAG.
- The deterministic industrial engine and Core parallel-run service persist A/B/C results and replay hashes.

## Pre-Day-21 executable path and disconnected implementations

The external ingestion path stops at `normalized_external_records`. It does not create provenance resources,
ontology entities, graph relationships, a quantitative baseline or a new world version. GraphRAG requires an
existing world version, while the current natural-language endpoint creates a separate hard-coded JSON world and
therefore bypasses ingestion, ontology, entity resolution, graph projection, retrieval and GraphRAG.

The simulator reads only `world_versions.state.companies` and `supplyLinks`; it does not read the ontology graph.
GraphRAG and simulation consequently do not share a data-built world. The Day 21 query interpreter also contains a
Taiwan/semiconductor string catalogue and invented baseline values. It is not a production Phase 1 path.

## Minimum preservation-first change

1. Add a scheduled-ingestion coordinator, disabled by default, using existing adapters and ingestion runs.
2. Add a deterministic normalized-record mapper and baseline builder. It must create source/evidence links,
   ontology-validated entities and relationships, resolve exact identifiers, and produce an immutable versioned
   simulation baseline with value-level provenance.
3. Add an explicit world-version manifest: as-of time, source snapshots, ontology version, graph projection version,
   simulation-rule version and assumptions.
4. Compile ontology relationships into a quantitative simulation graph. Missing required parameters must produce
   `INSUFFICIENT_DATA`, never a numeric default.
5. Replace the catalogue interpreter with OpenAI structured extraction. Core, not the LLM, resolves the extracted
   target, validates the metric and creates the shock definition.
6. Query GraphRAG and run simulation against the exact same world-version id. Return paths, evidence, provenance
   classes, the manifest and replay hashes together.
7. Keep the existing world fork, parallel-run persistence and deterministic AI engine. Add relationship-rule handlers
   incrementally rather than rewriting these components.

## Phase 1 acceptance gate

The E2E gate must prove `ingestion -> evidence -> resolution -> ontology graph -> baseline/world version -> graph
projection -> GraphRAG -> shock definition -> A/B/C simulation -> result with provenance`. A hard-coded company,
country, relationship or coefficient in the public request path is a release failure. Missing quantitative coverage
must return `INSUFFICIENT_DATA` while still returning the supported graph path and evidence.

## Implemented connection

The completion handler now turns changed normalized records into formal provenance, ontology-validated graph objects,
retrieval documents and the next immutable version of the external-data World. UN Comtrade observations compile a
relationship simulation baseline with derived dependency ratios. SEC/OpenDART records create resolvable company
entities but intentionally remain quantitatively insufficient for production/capacity scenarios. The natural-language
endpoint uses OpenAI only for bounded structure, then resolves and retrieves against the same World Version used by
the relationship engine and persisted A/B/C runs. Neo4j is used when enabled; otherwise GraphRAG traverses the same
authoritative PostgreSQL graph instead of becoming disconnected.

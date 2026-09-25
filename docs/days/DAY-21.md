# Day 21: grounded Phase 1 integration

Status: implemented and promoted by the grounded E2E gate.

Day 21 keeps the Day 5 deployment topology: one EC2 host, Nginx, Docker Compose, PostgreSQL, SSM and S3. Neo4j
remains an optional external projection selected by configuration; PostgreSQL remains the authoritative graph and
provides path traversal when Neo4j is disabled. Scheduled ingestion is implemented but disabled by default.

## Executable path

```text
external API → ingestion run/raw/normalized record → Source/Evidence/Provenance
→ deterministic entity and relationship mapping → ontology validation → entity resolution
→ PostgreSQL world graph → optional Neo4j projection → immutable World Version
→ natural-language input → OpenAI structured candidate → Core entity resolution
→ same-version GraphRAG → USER_ASSUMPTION ShockDefinition
→ relationship rule traversal → A/B/C persisted runs → evidence, paths and replay hashes
```

The public request contains only natural language. OpenAI extracts target, metric, percentage change, duration and an
optional policy; it does not resolve entities or generate baseline values, coefficients or results. Core resolves the
target inside the selected World Version, validates quantitative coverage and builds the shock. UN Comtrade amounts
are `OBSERVED`; totals and dependency ratios are deterministic `DERIVED` values; the requested change is
`USER_ASSUMPTION`. A missing metric or relationship parameter returns `INSUFFICIENT_DATA` and no invented number.

SEC and OpenDART observations create identifier-resolved company entities and searchable evidence. They do not imply
production or capacity values; company production scenarios therefore fail closed until a supported quantitative
observation exists. UN Comtrade export observations create country nodes, `TRADE_FLOW` relationships and the current
relationship-graph baseline. Each successful changed ingestion creates the next version of the stable
`External data baseline` World rather than mutating an old version.

## Parallel branches

- A applies the requested shock only.
- B explicitly records that an optional policy is not numerically applied until a versioned policy rule exists.
- C applies the shock with `OBSERVED_ALTERNATIVE_SUPPLY`, using only alternative inbound dependency shares derived
  from the same World Version.

All branches start from the same version and seed. The simulator dispatches propagation through separate handlers for
`TRADE_FLOW`, `SUPPLIES`, `DEPENDS_ON`, `PRODUCES` and `SHIPS_VIA`. Every handler requires a bounded, provenance-backed
`dependencyRatio`; an unsupported relationship or absent parameter is rejected rather than assigned a coefficient.

## Configuration and gate

`INGESTION_SCHEDULE_ENABLED=false` is the safe default. When enabled, `INGESTION_SCHEDULE_ACTOR_ID` identifies the
audited actor and `INGESTION_SCHEDULE_SOURCES_JSON` supplies per-source `enabled`, Spring cron and `parametersJson`.
External API and OpenAI credentials stay in environment/SSM secrets.

`day21-e2e.sh` is the promotion gate. It requires a data-built READY World and verifies the public Web → Core → AI
path, resolved country entity, GraphRAG evidence, three persisted branches, invariants and replay hashes. Health-only
smoke tests do not manufacture a demo World; set `RUN_GROUNDED_E2E=true` only after real ingestion.

The World manifest records both expected and successfully indexed retrieval-document counts. A World is simulation
ready only when the counts match and the index status is `READY`. Deployment performs one bounded UN Comtrade bootstrap
ingestion before the gate; recurring ingestion remains implemented but disabled by default.

## Verified local integration

The full Compose stack was verified with an actual UN Comtrade China→Republic of Korea HS8542 observation. The
resulting immutable World Version contained two nodes and one `TRADE_FLOW` edge, indexed both current source records
(`2/2`) with `text-embedding-3-small`, and completed the Korean query `중국 반도체 공급이 50% 감소하면?`. The response
resolved `China` as `COUNTRY`, returned GraphRAG paths/evidence/citations, baseline and relationship provenance, and
three persisted deterministic branches with passing invariants. Repeating the same request created a new scenario
without violating immutable scenario-name constraints while reusing the exact same World Version and numerical seed.

Production release `day21-grounded-20260924-r3` passed the gate on the existing Day 5 topology. The deployment created
World Version `2cfece15-416b-44e2-9783-ea4db0d8a69f` from the bounded UN Comtrade bootstrap ingestion, returned a
completed public v2 scenario with one graph path, three graph evidence entries, a retrieval citation and three passing
branches, and stored its resource baseline under `baselines/day21-grounded-20260924-r3.json` in the existing artifact
bucket.

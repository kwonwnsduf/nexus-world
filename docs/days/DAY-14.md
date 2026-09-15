# Day 14: Entity resolution and Neo4j graph projection

Status: Complete

Day 14 turns the versioned PostgreSQL economic-civilization graph into a Neo4j read model without moving ownership of
world state. Operators can resolve source entities, replace a world-version projection, and query bounded paths of up
to three hops across industrial and society nodes.

## Entity resolution

`POST /api/v1/world-versions/{versionId}/graph/entities/resolve` performs deterministic exact-key resolution. It accepts
only trusted identifier namespaces (`LEI`, `CIK`, `DART_CORP_CODE`, ISO country identifiers, `UN_LOCODE`, HS/ISIC and
other versioned codes). Values are Unicode-normalized, trimmed and case-folded. Names and aliases are never automatic
merge keys.

Each identifier is unique within `(world version, entity type, scheme)`. If supplied identifiers point to different
entities, the whole transaction is rejected with `409 Conflict`. A successful repeat returns `MATCHED`; a new identity
returns `CREATED`. Matching does not silently overwrite attributes from another source.

## Projection and traversal

`POST /api/v1/world-versions/{versionId}/graph/projections` reads the complete PostgreSQL snapshot and replaces only the
matching world version in one Neo4j transaction. Projection runs and failure summaries remain audited in PostgreSQL.
Nested graph attributes are retained as JSON; node and relationship UUIDs remain stable.

`GET /api/v1/world-versions/{versionId}/graph/paths/{rootEntityId}?maxDepth=3` returns ordered paths constrained to the
requested world version. Depth is hard-limited to 1–3 and result count is capped to prevent accidental unbounded graph
queries.

Neo4j is optional for ordinary health checks. To run the local graph profile:

```powershell
$env:NEO4J_ENABLED="true"
docker compose --profile platform up --build -d
```

## Verification

- Unit coverage proves exact-key idempotency, Unicode/case normalization, fuzzy-name refusal and conflicting-key refusal.
- Spring integration coverage exercises the authenticated resolution contract and database constraints.
- A Testcontainers Neo4j integration test projects Facility → Company → Demographic Cohort → Household Archetype and
  verifies the complete three-hop industrial-to-society path.
- Contract validation covers resolve, projection and bounded path endpoints.

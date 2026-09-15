# Economic Civilization Ontology v1

The ontology is an executable contract, not only a glossary. Core API persists a graph per immutable `WorldVersion`,
and PostgreSQL rejects relationships whose endpoint types or world versions do not match the catalog.

## Modeling rules

- Stable `naturalKey` values identify the same conceptual entity inside one world version; UUIDs are internal identities.
- Every node and edge may have a validity interval. Historical truth is appended in a new world version.
- Attributes can be linked to Day 6 evidence or assumptions through a property-level `ProvenanceLink`.
- Society nodes exposed by the graph are weighted aggregates. Cohort, household archetype, labor/housing market, and social indicator types are `aggregateOnly`.
- Real-person and raw-household node types do not exist. Personal identifier fields are rejected recursively.
- Quantitative state transitions remain deterministic simulation behavior; graph attributes describe state and calibrated inputs.

## Domains and entity types

| Domain | Entity types |
|---|---|
| Geography | `COUNTRY`, `REGION`, `PORT` |
| Industry | `INDUSTRY`, `COMPANY`, `FACILITY`, `MATERIAL`, `COMPONENT`, `PRODUCT` |
| Government | `GOVERNMENT`, `POLICY` |
| Finance | `BANK` |
| Society | `LABOR_MARKET`, `DEMOGRAPHIC_COHORT`, `HOUSEHOLD_ARCHETYPE`, `OCCUPATION`, `SKILL`, `WELFARE_BENEFIT`, `HOUSING_MARKET`, `SOCIAL_INDICATOR` |
| Event | `CRISIS_EVENT` |

Each Entity Type retains `requiredAttributes` for v1 backward compatibility. The authoritative validation source is the
Property Type catalog described below.

## Property types

Every declared property records its owning `entityType`, code, display name, description, data type, required flag, and
optional unit and classification system. Supported data types are `STRING`, `INTEGER`, `NUMBER`, `BOOLEAN`, `DATE`,
`DATE_TIME`, `UUID`, `URI`, `OBJECT`, and `ARRAY`.

`world_graph_entities.attributes` remains JSONB and contains the actual values. Entity creation loads the Property Type
definitions, rejects missing required values, and validates every declared value's data type and format. Undeclared
extension attributes remain accepted for backward compatibility; privacy checks still inspect them recursively.

| Entity | Property | Type | Required | Unit/classification |
|---|---|---|---:|---|
| `COMPANY` | `industryCode` | `STRING` | yes | ISIC |
| `DEMOGRAPHIC_COHORT` | `baseYear` | `INTEGER` | yes | year |
| `DEMOGRAPHIC_COHORT` | `weight` | `NUMBER` | yes | persons |
| `CRISIS_EVENT` | `startedAt` | `DATE_TIME` | yes | ISO-8601 |

## Executable relationship grammar

```text
CRISIS_EVENT -AFFECTS-> REGION <-LOCATED_IN- FACILITY <-OPERATES- COMPANY
COMPANY -CLASSIFIED_AS-> INDUSTRY <-REGULATES- GOVERNMENT
FACILITY -PRODUCES-> COMPONENT -REQUIRES-> MATERIAL
COMPANY -SUPPLIES-> COMPANY

GOVERNMENT -ENACTS-> POLICY -PROVIDES-> WELFARE_BENEFIT
COMPANY -EMPLOYS-> DEMOGRAPHIC_COHORT -MEMBER_PROFILE_OF-> HOUSEHOLD_ARCHETYPE
DEMOGRAPHIC_COHORT -PARTICIPATES_IN-> LABOR_MARKET -DESCRIBES-> REGION
HOUSEHOLD_ARCHETYPE -CONSUMES-> PRODUCT
HOUSEHOLD_ARCHETYPE -SAVES_AT/BORROWS_FROM-> BANK -LENDS_TO-> COMPANY
```

The database stores endpoint types on every edge. Composite foreign keys require the verb/type triple to exist and both
endpoints to have that exact type in the same world version. Invalid edges therefore fail even outside the application.

## Action types

Action Types contain a stable code, actor and target types, description, and JSON Schema parameter contract.

| Action | Actor | Target | Validation |
|---|---|---|---|
| `REDUCE_PRODUCTION` | `COMPANY` | `FACILITY` | `reductionRatio` between 0 and 1 |
| `ENACT_POLICY` | `GOVERNMENT` | `POLICY` | ISO-8601 `effectiveFrom` |
| `ADJUST_CREDIT` | `BANK` | `COMPANY` | numeric `creditLimitDelta` |

Validation is deliberately side-effect free. Day 7 creates no Action record, world mutation, simulation decision,
business behavior, or Agent invocation.

## API and evolution

```text
GET  /api/v1/ontology
POST /api/v1/ontology/actions/validate
POST /api/v1/world-versions/{versionId}/graph/entities
POST /api/v1/world-versions/{versionId}/graph/relationships
GET  /api/v1/world-versions/{versionId}/graph
```

Writes require `ANALYST`, `OPERATOR`, or `ADMIN`. `economic-civilization-v1` is additive and stable. PostgreSQL owns
transactional world state. Neo4j receives atomically replaced, world-version-scoped read projections and serves bounded
one-to-three-hop traversal only; it is never written back into transactional state.

Entity resolution is deliberately conservative. Only exact, namespaced identifiers such as LEI, CIK, DART corporate
code, ISO/UN location codes and versioned classification codes may auto-match. Display-name or fuzzy-name matching is
not accepted. If exact identifiers disagree, the candidate is rejected for review instead of being merged.

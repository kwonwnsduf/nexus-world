# Day 6: Evidence, assumptions, and source provenance

Status: Complete (2026-09-11)

## Outcome

Day 6 establishes the common provenance boundary used by later ingestion, RAG, graph, and simulation work. The Core API
can register sources, extract evidence, declare assumptions, and attach either origin to a specific property of any
versioned domain subject.

## Delivered

- Flyway migration `V4__create_provenance_foundation.sql` creates `data_sources`, `evidence_items`, `assumptions`, and
  `provenance_links` with PostgreSQL JSONB fields, foreign keys, checks, partial indexes, and duplicate-link protection.
- JPA domain models and repositories own the transactional records in Core API.
- Authenticated v1 endpoints create and read provenance records. Writes require `ANALYST`, `OPERATOR`, or `ADMIN`;
  `VIEWER` remains read-only.
- Every write captures the authenticated user and UTC creation time.
- The API validates source identities, absolute URIs, SHA-256 digests, evidence payloads, confidence ranges, validity
  periods, JSON object fields, JSON Pointer property paths, and the evidence/assumption exclusive-or rule.
- Versioned JSON Schema and OpenAPI contracts describe the public payloads before later adapters consume them.
- `docs/DATA_CATALOG.md` defines classification, locator, lifecycle, and quality conventions.

## API surface

```text
POST /api/v1/sources
GET  /api/v1/sources/{id}
POST /api/v1/evidence
GET  /api/v1/evidence/{id}
POST /api/v1/assumptions
GET  /api/v1/assumptions/{id}
POST /api/v1/provenance-links
GET  /api/v1/provenance-links?subjectType=...&subjectId=...
GET  /api/v1/provenance-links/{id}
```

Creation is intentionally append-oriented. Day 6 exposes no destructive endpoint, so later simulation results cannot
lose their cited origin. Corrections use a new record; an audited lifecycle can be added with the later audit work.

## Completion evidence

- Migration integration runs all four migrations twice and validates the resulting PostgreSQL schema.
- End-to-end Core API integration creates a source, evidence, assumption, and two property-level links against real
  PostgreSQL, then reads the complete provenance chain as a viewer.
- Integration tests prove invalid mixed origins are rejected and viewer writes are forbidden.
- Architecture tests preserve API/application/infrastructure package boundaries.

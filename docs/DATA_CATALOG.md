# Data catalog

## Provenance classes

NEXUS WORLD separates observed material from model choices. Every ingested artifact is a `Source`; a specific usable
observation extracted from it is an `Evidence`; and an explicit modelling choice without direct observation is an
`Assumption`. A `ProvenanceLink` attaches exactly one evidence item or assumption to one property of a versioned domain
record.

| Class | Stable identity | Required provenance fields | Lifecycle |
|---|---|---|---|
| Source | `sourceKey` | type, title, retrieval time, creator | Append; register a new key/version when content changes |
| Evidence | UUID | source, claim, locator, payload, confidence, creator | Append-only |
| Assumption | `assumptionKey` | category, statement, rationale, status, confidence, creator | New records are preferred when meaning changes |
| ProvenanceLink | UUID | subject type/id, JSON Pointer property path, exactly one origin | Append-only |

`sourceKey` and `assumptionKey` are machine-stable identifiers and must not contain credentials. `canonicalUri` may
identify an HTTP API, web page, object, or dataset landing page. `contentSha256` is the lowercase SHA-256 of the exact
retrieved content when content bytes are available. Source metadata, evidence locators, measured values, assumed values,
and transformations are JSON so future adapters can retain source-native detail without changing the relational core.

## Evidence locator conventions

Locators are JSON objects. Adapters should use the smallest applicable coordinates:

- document: `page`, `section`, `bbox`, `charStart`, `charEnd`;
- table: `sheet`, `table`, `row`, `column`, `cell`;
- API: `endpoint`, `query`, `responsePath`;
- dataset: `dataset`, `series`, `dimensions`, `period`.

The optional excerpt is a short verification aid, not a copy of the source. Structured observations belong in
`measuredValue`, including their unit when applicable.

## Provenance link conventions

`subjectType` uses an uppercase ontology name such as `WORLD_VERSION`, `COMPANY`, or `SIMULATION_METRIC`.
`subjectId` identifies the versioned record, and `propertyPath` is an RFC 6901 JSON Pointer. The root pointer is the empty
string. A transformation object records reproducible operations such as `weighted_mean`, input weights, code version,
or formula identifier. It must never contain executable code or secrets.

The polymorphic subject cannot use a database foreign key. Until each owning aggregate exposes transactional provenance
registration, `subjectId` is an authenticated caller-supplied opaque identifier; callers must only link existing,
versioned records. Evidence and assumptions are protected by foreign keys, and a link cannot contain both or neither.

## Quality rules

- Observed facts use evidence; unobserved parameters use assumptions. They are never silently interchanged.
- Retrieval time is mandatory, publication time is optional, and retrieval cannot precede publication.
- Confidence is in the closed interval `[0, 1]` and expresses source/assumption confidence, not prediction probability.
- Evidence must contain a non-blank excerpt or a structured measured value.
- Validity end requires a validity start and cannot precede it.
- `ANALYST`, `OPERATOR`, and `ADMIN` may register provenance; every authenticated role may read it.
- Deletes and mutation endpoints are deliberately absent. Retention and audited correction are introduced with the
  later audit lifecycle rather than destroying provenance.

## External ingestion catalog (Days 8-13)

Every retrieval creates an `ingestion_runs` audit record. Exact response bytes are retained in
`raw_ingestion_payloads`; canonical observations are stored separately in `normalized_external_records`. A normalized
row points to its raw payload and carries the redacted request URI, SHA-256, adapter name, and transformation version.
Invalid rows are quarantined in `ingestion_rejections` instead of being silently discarded.

| Source | Transport | Canonical record | Required request parameters |
|---|---|---|---|
| SEC | EDGAR submissions JSON | `SEC_FILING` | `cik` |
| OpenDART | disclosure list JSON | `OPENDART_FILING` | `startDate`, `endDate`; optional `corpCode` |
| UN Comtrade | trade API JSON | `TRADE_FLOW` | `reporterCode`, `period`; optional partner/flow/HS code |
| World Bank | Indicators API v2 JSON | `MACRO_INDICATOR` | `country`, `indicator`; optional `date` |
| USGS | FDSN GeoJSON | `EARTHQUAKE` | `startTime`, `endTime`; optional magnitude |
| UN/LOCODE | official ZIP/CSV | `LOCATION_CODE` | optional release `version` |
| WPI | official CSV | `PORT` | optional data `version` |
| HS | official JSON classification | `HS_CLASSIFICATION` | optional `version` |
| ISIC | official CSV classification | `ISIC_CLASSIFICATION` | optional `version` |
| UN WPP | official bulk CSV/GZIP | `POPULATION_TARGET` | optional `version` |
| ILOSTAT | bulk CSV/GZIP | `LABOR_INDICATOR` | `dataset` |
| KOSIS | statistics OpenAPI JSON | `KOREAN_STATISTIC` | `userStatsId`, `orgId`, `tableId`, start/end period |
| OECD | SDMX-CSV | `OECD_STATISTIC` | `flowRef`; optional key/start/end period |

Credentials are injected from the environment and are redacted before request URIs are persisted. Country code scheme,
classification/version, currency, unit, period, data version, source-native dimensions, and source record are preserved.

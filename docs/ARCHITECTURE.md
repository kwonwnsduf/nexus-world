# Architecture v0.1

## Deployable units

- `web`: Next.js user interface and backend-for-frontend endpoints.
- `core-api`: Spring Boot system of record for users, worlds, scenarios, jobs, approvals, and audit.
- `ai-service/worker`: FastAPI and Python workers for retrieval, agents, population synthesis, and simulation.
- `n8n`: later event automation; not part of the Day 2 runtime.

## Dependency rules

```text
web -> versioned Core HTTP/SSE contracts
core-api -> versioned AI HTTP/event contracts
ai-service -> owned simulation/read-model storage and versioned events
all services -> observability interfaces
```

- Web has no database credentials.
- Each service writes only its owned schema/tables.
- Synchronous contracts are OpenAPI; asynchronous contracts are JSON Schema.
- PostgreSQL is canonical relational storage, Neo4j is a derived relationship index, Redis is ephemeral, and Kafka-compatible events are integration messages rather than canonical state.
- Health endpoints are shallow, deterministic, and independent of LLMs or external data providers.

## Architecture enforcement

- Java controllers must reside in an `api` package; API code must not depend directly on future infrastructure adapters.
- Python API modules may call application services but must not import persistence or provider adapters directly.
- CI validates service builds, tests, static analysis, contracts, and an isolated Compose smoke test.

## Day 2 vertical contract

```text
browser
  -> web BFF: GET /api/platform/status
    -> core-api: GET /api/v1/platform/status
      -> ai-service: GET /api/v1/platform/capabilities
```

Each hop validates the versioned `v1` payload. The browser receives platform capability metadata through the web BFF and never calls an internal service directly. A downstream outage is converted into a bounded 502/503 response rather than hanging indefinitely.

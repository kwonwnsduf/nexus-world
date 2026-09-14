# NEXUS WORLD

Evidence-grounded economic civilization and supply-chain digital twin.

## Day 6 services

| Service | URL | Health |
|---|---|---|
| Web | http://localhost:3000 | `/api/health` |
| Core API | http://localhost:8080 | `/actuator/health` |
| AI Service | http://localhost:8000 | `/health` |

## Prerequisites

- Git
- Java 17 (for local Core API checks)
- Node.js and Corepack (for local Web checks)
- Docker Desktop with Linux containers

Python does not need to be installed locally; the AI checks can run in Docker.

The first versioned vertical contract is live at Web `/api/platform/status`, Core `/api/v1/platform/status`, and AI `/api/v1/platform/capabilities`.
Core API owns the PostgreSQL schema. Flyway applies the versioned migrations in
`services/core-api/src/main/resources/db/migration` when the service starts.

Core API also owns local JWT authentication. Compose creates an idempotent local-only administrator using the `BOOTSTRAP_ADMIN_*` values from `.env.example`. Login returns a short-lived access token and rotating refresh token; logout revokes the refresh family and blacklists the active access token. Cognito and OAuth login are not used.

The first AWS deployment uses Terraform, one Amazon Linux EC2 instance, Docker Compose, and host Nginx. Only ports 80 and 443 are public; administration and deployments use AWS Systems Manager without SSH. Runtime secrets live in SSM Parameter Store, while release archives and encrypted PostgreSQL backups live in a private S3 bucket. See [docs/days/DAY-05.md](docs/days/DAY-05.md).

Day 6 adds the append-oriented Source, Evidence, Assumption, and property-level Provenance Link foundation. It records
which facts were observed, which values were assumed, who registered them, and how they map to later domain state. See
[docs/days/DAY-06.md](docs/days/DAY-06.md) and [docs/DATA_CATALOG.md](docs/DATA_CATALOG.md).

Days 8-13 add production-shaped external ingestion for SEC, OpenDART, UN Comtrade, World Bank, USGS, UN/LOCODE, WPI,
HS, ISIC, UN WPP, ILOSTAT, KOSIS, and OECD. Configure credentials in an untracked `.env`, log in as an administrator,
then start a source run through `POST /api/v1/admin/ingestions/{source}`. For example:

```json
{
  "parameters": {
    "cik": "0000320193",
    "includeCompanyFacts": true
  }
}
```

Run status is available from `GET /api/v1/admin/ingestions/{id}`. Exact raw payloads, canonical rows, rejected rows, and
credential-redacted provenance are stored separately. Live network tests are opt-in with
`RUN_LIVE_INGESTION_TESTS=true`; fixture tests always run in CI. Day 14 entity resolution and Neo4j loading are not part
of this implementation. See [docs/DATA_CATALOG.md](docs/DATA_CATALOG.md).

## Start from a clean clone

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\doctor.ps1
docker compose up --build -d
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
docker compose down
```

PostgreSQL starts with the default stack. Start the remaining optional local platform dependencies with:

```powershell
docker compose --profile platform up -d
```

## Local checks

See [AGENTS.md](AGENTS.md) for the complete required command set. Day 0 decisions live under `docs/`; API and event contracts live under `contracts/`.

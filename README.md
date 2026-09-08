# NEXUS WORLD

Evidence-grounded economic civilization and supply-chain digital twin.

## Day 2 services

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

## Start from a clean clone

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\doctor.ps1
docker compose up --build -d
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
docker compose down
```

Start optional local platform dependencies with:

```powershell
docker compose --profile platform up -d
```

## Local checks

See [AGENTS.md](AGENTS.md) for the complete required command set. Day 0 decisions live under `docs/`; API and event contracts live under `contracts/`.

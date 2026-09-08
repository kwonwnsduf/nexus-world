# NEXUS WORLD engineering guide

## Scope

This repository contains the NEXUS WORLD web application, core API, and AI/simulation service. The current baseline includes the Day 2 versioned Web → Core → AI platform contract. Do not add AWS deployment, authentication, RAG, ingestion, or simulation behavior unless the active task explicitly includes it.

## Service boundaries

- `apps/web` calls public HTTP/SSE contracts. It never connects directly to a database.
- `services/core-api` owns transactional world, scenario, job, approval, and audit state.
- `services/ai-service` owns retrieval, agent orchestration, synthetic population generation, and simulation computation.
- Cross-service payloads must be versioned under `contracts/` before implementation.
- Services must not write another service's owned tables. Expensive AI and external APIs must never be required for health checks.

## Required checks

Run checks for every affected service before handing off:

```powershell
corepack pnpm install --frozen-lockfile
corepack pnpm web:lint
corepack pnpm web:typecheck
corepack pnpm web:test
corepack pnpm web:build
.\services\core-api\gradlew.bat -p .\services\core-api clean test bootJar
docker build --target test -t nexus-world-ai-test .\services\ai-service
docker compose config
docker compose up --build -d
powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
docker compose down
```

## Safety and quality

- Java source level is 17.
- Gradle Wrapper is the only supported Core API build entry point; Maven files must not be added.
- Do not commit `.env`, credentials, generated data, or synthetic person rows.
- Use seeded deterministic transitions for future simulation code.
- Numerical state changes belong in simulation code, not LLM output.
- Public society APIs expose aggregates only and must suppress small cells.

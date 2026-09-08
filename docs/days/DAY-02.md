# Day 2 — First vertical service contract

## Goal

Prove the monorepo boundaries with one small, versioned request that travels from the Next.js BFF through Spring Boot to FastAPI without requiring a database, cloud account, or model API.

## Completed

- Replaced the Core API Maven build with a Java 17 Gradle Wrapper build.
- Added versioned OpenAPI documents for Core API and AI Service plus a shared JSON Schema.
- Added `GET /api/v1/platform/capabilities` to AI Service.
- Added `GET /api/v1/platform/status` to Core API using an outbound application port and HTTP adapter.
- Added `GET /api/platform/status` to the web BFF with runtime payload validation and a bounded timeout.
- Displayed the connected platform status on the web home page.
- Added unit, integration, architecture, contract, and end-to-end Compose smoke checks.
- Updated GitHub Actions to build the Java 17 service with Gradle.

## Boundary decisions

- Browsers call only the web BFF; internal service locations stay server-side.
- Core API depends on an application-owned interface, not directly on its HTTP adapter.
- AI Service reports deterministic capabilities without invoking external APIs or expensive models.
- Every public Day 2 payload declares `contractVersion: v1`.

## Deferred

Persistence, authentication, ingestion, RAG, simulation behavior, synthetic populations, Kafka, and AWS deployment remain scheduled for later days in the 56-day plan.

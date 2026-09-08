# Day 1 — Monorepo foundation

## Definition of done

- [x] Git repository uses `main`.
- [x] Java 17, Node, and Python container toolchains are pinned.
- [x] Web lint, typecheck, test, and production build pass.
- [x] Core API tests and Java 17 build pass through its wrapper.
- [x] AI lint, typecheck, and tests pass in its test image.
- [x] Compose configuration validates.
- [x] Web, Core API, and AI Service report healthy through Compose.
- [x] Smoke tests fail fast and return non-zero on an unhealthy service.
- [x] CI runs the same checks without real secrets.
- [x] Architecture and privacy boundaries are documented and tested where executable.

Verified locally on 2026-09-07. GitHub-hosted execution begins only after a remote repository is created, which is intentionally outside Day 1 execution scope.

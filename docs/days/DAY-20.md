# Day 20: Immutable world fork and parallel branches

Status: Complete

Day 20 adds Core API ownership of simulation worlds, parallel scenarios, branch inputs, run lifecycle, and turn
checkpoints. `POST /api/v1/worlds` creates a world and immutable baseline version.
`POST /api/v1/world-versions/{versionId}/parallel-simulations` atomically creates two or three branches and then executes
each through the Day 19 numerical engine. `GET /api/v1/parallel-simulations/{scenarioId}` returns branch status, replay
hashes, final states, invariant results, and ordered checkpoints.

## Isolation guarantees

- A baseline world version cannot be updated or deleted; PostgreSQL rejects both operations.
- Each branch stores its own JSON input and each run stores its own seed, request hash, result hash, final state, and
  checkpoints.
- Branch setup is one database transaction. Numerical calls occur after that transaction, avoiding a database lock
  across a service boundary.
- A failed numerical call marks only its run and branch failed with a bounded diagnostic; it never mutates the baseline
  or a completed sibling.
- The branch seed is derived deterministically as `requestedSeed + stableBranchIndex`, with overflow rejection.
- Parent-branch references, when later used for nested forks, are constrained to the same scenario.

The Core API never calculates numerical transitions. It constructs the versioned request, records its SHA-256 hash,
delegates computation to the AI/simulation service, and persists the returned checkpoints transactionally.

# Day 19: Deterministic industrial simulation core

Status: Complete

Day 19 adds a versioned, purely numerical industrial simulation endpoint at
`POST /api/v1/simulations/execute` in the AI/simulation service. The engine accepts a bounded company state,
supply links, explicit turn-scoped shocks, a seed, and one to 120 turns. It emits every turn snapshot, the final state,
invariant results, and a canonical SHA-256 replay hash. It makes no LLM or external API call.

## Transition and accounting rules

Each turn uses the prior turn's inventory as the input boundary. Companies are processed in stable ID order; shared
supplier inventory is reserved deterministically, so two customers cannot consume the same units. Production is bounded
by baseline output, effective capacity, effective demand, productivity, and every required input. Finished inventory,
sales, unmet demand, revenue, variable/input cost, wage bill, operating profit, next-turn demand, and next-turn labor
demand are then calculated and rounded at the explicit six-decimal accounting boundary.

The engine checks non-negative state, capacity, supply-flow limits, and revenue accounting on every turn. An invalid
company reference, duplicate link, self-link, out-of-range shock, or shock beyond the run horizon is rejected before
execution. Identical requests produce an identical result hash; different seeds alter only the bounded productivity
modifier.

## Verification

The test suite covers deterministic three-turn replay, seed sensitivity, supplier-to-customer shock propagation,
capacity and flow limits, non-negative inventory, API validation, Ruff, strict mypy, and pytest. The JSON Schema contract
is `contracts/schemas/simulation-contract-v1.json`.

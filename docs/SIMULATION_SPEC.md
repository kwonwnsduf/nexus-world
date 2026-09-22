# Simulation specification v0.1

Each turn applies crisis and policy inputs, computes supply and production, prices and revenue, lagged labor demand, employment and wages, household income, taxes and contributions, transfers, disposable income, consumption/savings/debt, fiscal state, credit risk, social metrics, invariants, and finally a checkpoint.

## Required invariants

- Identical versioned inputs and seed produce identical output.
- Population changes only through explicit demographic events.
- Paid wages equal received labor income at the selected accounting boundary.
- Paid taxes equal government tax receipts; transfers reconcile in both directions.
- Household consumption and housing costs do not exceed available resources plus allowed credit.
- A branch cannot mutate its parent or sibling state.

LLMs may propose bounded actions but cannot directly mutate numerical state or invent tax, wage, eligibility, or benefit values.

## Industrial engine v1 (Day 19)

The executable contract is `contracts/schemas/simulation-contract-v1.json`. A turn consumes prior-turn supplier
inventory, applies explicit bounded shocks, reserves shared inputs in stable company-ID order, and computes production,
sales, ending inventory, revenue, costs, profit, lagged demand, and labor demand. Numerical work is isolated in the
AI/simulation service and never depends on an LLM. Results are rounded to six decimal places and addressed by a
canonical SHA-256 replay hash.

## Parallel worlds v1 (Day 20)

Core API owns immutable baselines, scenarios, branches, runs, and checkpoints. A parallel request creates two or three
branch inputs in one transaction, then executes them independently. Branch state is persisted only under its run;
baseline world versions are protected by a database immutability trigger. External engine calls never run inside the
branch-creation transaction.

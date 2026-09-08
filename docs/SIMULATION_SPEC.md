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


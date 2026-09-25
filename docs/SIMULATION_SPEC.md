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

## Relationship-path propagation

`relationship-graph-v1` propagates an explicit metric shock in stable relationship-ID order. For a path `A → B → C`,
the engine applies the A→B relationship rule and dependency ratio first, then uses that propagated change as the input
to the B→C rule. `maxPropagationDepth` bounds this chain and every snapshot records `traversal.steps` with its depth,
edge, input change, and propagated change.

The Core parallel-simulation request can choose the topology source:

```json
{
  "scenarioName": "Semiconductor supply path",
  "seed": 42,
  "turns": 3,
  "graphTraversal": {"mode": "NEO4J_PATHS", "maxDepth": 3},
  "branches": [
    {
      "name": "shock",
      "shocks": [
        {"entityId": "country:UN-M49:410", "metric": "SUPPLY", "change": -0.2,
         "turn": 1, "duration": 2, "basisType": "USER_ASSUMPTION"}
      ]
    },
    {"name": "control", "shocks": []}
  ]
}
```

`NEO4J_PATHS` requires `NEO4J_ENABLED=true` and a completed projection for that exact world version. The numeric engine
still refuses missing `dependencyRatio` values and does not ask an LLM to create them. Omit `graphTraversal` (or use
`WORLD_VERSION`) for the previous snapshot-only behavior.

## Multi-source observed baselines

The relationship graph is no longer built from UN Comtrade values alone. The latest compatible
observations are compiled into versioned node metrics as follows:

| Source | Simulation contribution |
|---|---|
| UN Comtrade | Country `SUPPLY`/`DEMAND` totals and directed `TRADE_FLOW` propagation edges |
| World Bank | Country metrics named `WORLD_BANK_<indicator>` |
| OECD | Country metrics named `OECD_<measure>` |
| KOSIS | Korean country metrics named `KOSIS_<item>` |
| ILOSTAT | Aggregate labor metrics named `ILOSTAT_<indicator>`; disaggregated sex/classification rows remain evidence rather than being silently summed |
| UN WPP | Aggregate population metrics named `UN_WPP_<indicator>`; age/sex detail remains evidence |
| SEC | Latest non-negative XBRL company facts named `SEC_<taxonomy>_<concept>` |
| OpenDART | Korean/English company identity aliases used to attach matching SEC company metrics to one company node |

ISO alpha-2 country codes are canonicalized to alpha-3, and Comtrade ISO fields are retained, so
macro, labor, population, and trade observations for the same country share a simulation node.
Every imported metric records its source system, normalized record ID, classification, unit, and
period. Classification catalogs, port/location reference files, disclosure metadata, and unlocated
USGS events stay available as retrieval/graph evidence but do not become numerical coefficients;
doing so would require a grounded quantitative mapping that those records do not currently contain.

The same ingestion snapshot also creates semantic graph entities and relationships for Neo4j:

| Source records | Entity and relationship projection |
|---|---|
| World Bank, OECD, KOSIS, ILOSTAT, UN WPP | `COUNTRY -[HAS_INDICATOR]-> STATISTICAL_INDICATOR` |
| SEC XBRL | `COMPANY -[HAS_INDICATOR]-> STATISTICAL_INDICATOR` |
| SEC SIC and OpenDART industry code | `COMPANY -[CLASSIFIED_AS]-> INDUSTRY` |
| WPI and port-capable UN/LOCODE rows | `PORT -[LOCATED_IN]-> COUNTRY` |
| HS parent codes | `PRODUCT -[PARENT_OF]-> PRODUCT` |
| ISIC codes | Versioned `INDUSTRY` entities |
| Commodity-level UN Comtrade | `COUNTRY -[PRODUCES]-> PRODUCT -[SUPPLIES]-> COUNTRY` plus `COUNTRY -[DEPENDS_ON]-> PRODUCT` |
| Comtrade mode-of-transport code | `PRODUCT -[SHIPS_VIA]-> TRANSPORT_MODE` |
| Geolocated USGS + WPI ports | `CRISIS_EVENT -[AFFECTS]-> PORT` when the port is within 500 km |

Each relationship points back to its normalized source record and evidence. These semantic edges
are traversable by Neo4j and GraphRAG, but the numerical simulation applies an edge only when it has
a grounded `dependencyRatio`; semantic membership is not silently treated as a quantitative impact.
The USGS-to-port edge is explicitly marked `MODEL_DERIVED` and records both distance and the
configured radius, so proximity is auditable and is not presented as an observed damage estimate.

# Domain ontology v0.1

Core aggregates are `World`, `WorldVersion`, `Scenario`, `ScenarioBranch`, `SimulationRun`, `TurnSnapshot`, `Evidence`, `Assumption`, and `Policy`.

Industrial entities include `Company`, `Facility`, `Region`, `Port`, `Material`, `Component`, `Product`, `Industry`, `TradeFlow`, and `CrisisEvent`.

Civilization entities include `SyntheticCitizen`, `SyntheticHousehold`, `DemographicCohort`, `Employment`, `IncomeSource`, `ConsumptionCategory`, `TaxRule`, `WelfareRule`, `Loan`, `Dwelling`, `LaborMarket`, and `SocialIndicator`.

Synthetic entities always carry a population model version, base year, seed, statistical weight, and source identifiers. They must never be linkable to real-person identifiers.


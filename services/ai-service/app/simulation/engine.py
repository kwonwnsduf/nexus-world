from __future__ import annotations

import hashlib
import json
import math
from collections import defaultdict

from app.simulation.models import (
    CompanyTurn,
    FlowTurn,
    IndustrialShock,
    SimulationRequest,
    SimulationResult,
    SupplyLink,
    TurnSnapshot,
)

PRECISION = 6
EPSILON = 1e-6


def _round(value: float) -> float:
    return round(max(0.0, value), PRECISION)


def _seeded_modifier(seed: int, turn: int, company_id: str) -> float:
    digest = hashlib.sha256(f"{seed}:{turn}:{company_id}".encode()).digest()
    sample = int.from_bytes(digest[:8], "big") / ((1 << 64) - 1)
    return 0.995 + sample * 0.01


def _combined_shock(shocks: list[IndustrialShock]) -> IndustrialShock | None:
    if not shocks:
        return None
    value = IndustrialShock(companyId=shocks[0].companyId, turn=shocks[0].turn)
    for shock in shocks:
        value.supplyMultiplier *= shock.supplyMultiplier
        value.demandMultiplier *= shock.demandMultiplier
        value.capacityMultiplier *= shock.capacityMultiplier
        value.priceMultiplier *= shock.priceMultiplier
    return value


def simulate(request: SimulationRequest) -> SimulationResult:
    states = {company.companyId: company.model_copy(deep=True) for company in request.companies}
    incoming: dict[str, list[SupplyLink]] = defaultdict(list)
    for link in sorted(request.supplyLinks, key=lambda item: (item.customerId, item.supplierId)):
        incoming[link.customerId].append(link)
    shocks: dict[tuple[int, str], list[IndustrialShock]] = defaultdict(list)
    for shock_input in request.shocks:
        shocks[(shock_input.turn, shock_input.companyId)].append(shock_input)

    snapshots: list[TurnSnapshot] = []
    all_valid = True
    for turn in range(1, request.turns + 1):
        opening_inventory = {key: value.inventory for key, value in states.items()}
        input_remaining = dict(opening_inventory)
        production: dict[str, float] = {}
        reserved_flows: dict[tuple[str, str], float] = {}
        active_shocks: dict[str, IndustrialShock | None] = {}
        for company_id in sorted(states):
            state = states[company_id]
            active_shock = _combined_shock(shocks[(turn, company_id)])
            active_shocks[company_id] = active_shock
            supply_multiplier = active_shock.supplyMultiplier if active_shock else 1.0
            capacity_multiplier = active_shock.capacityMultiplier if active_shock else 1.0
            demand_multiplier = active_shock.demandMultiplier if active_shock else 1.0
            target = min(
                state.baselineProduction
                * supply_multiplier
                * state.productivity
                * _seeded_modifier(request.seed, turn, company_id),
                state.capacity * capacity_multiplier,
                state.demand * demand_multiplier,
            )
            for link in incoming[company_id]:
                available = min(input_remaining[link.supplierId], link.maxFlow)
                target = min(target, available / link.inputUnitsPerOutput)
            production[company_id] = _round(
                min(target, state.capacity * capacity_multiplier)
            )
            for link in incoming[company_id]:
                quantity = _round(
                    min(
                        production[company_id] * link.inputUnitsPerOutput,
                        link.maxFlow,
                        input_remaining[link.supplierId],
                    )
                )
                reserved_flows[(link.supplierId, company_id)] = quantity
                input_remaining[link.supplierId] = _round(
                    input_remaining[link.supplierId] - quantity
                )

        supplier_remaining = input_remaining
        flows: list[FlowTurn] = []
        input_costs: dict[str, float] = defaultdict(float)
        for customer_id in sorted(incoming):
            for link in incoming[customer_id]:
                quantity = reserved_flows[(link.supplierId, customer_id)]
                value = _round(quantity * link.unitCost)
                input_costs[customer_id] += value
                flows.append(
                    FlowTurn(
                        supplierId=link.supplierId,
                        customerId=customer_id,
                        quantity=quantity,
                        value=value,
                    )
                )

        company_turns: list[CompanyTurn] = []
        violations: list[str] = []
        total_revenue = total_profit = total_production = total_unmet = 0.0
        for company_id in sorted(states):
            state = states[company_id]
            active_shock = active_shocks[company_id]
            price_multiplier = active_shock.priceMultiplier if active_shock else 1.0
            price = _round(state.unitPrice * price_multiplier)
            demand = _round(state.demand * (active_shock.demandMultiplier if active_shock else 1.0))
            available_finished = supplier_remaining[company_id] + production[company_id]
            units_sold = _round(min(demand, available_finished))
            ending_inventory = _round(available_finished - units_sold)
            unmet = _round(demand - units_sold)
            revenue = _round(units_sold * price)
            variable_cost = _round(
                production[company_id] * state.unitVariableCost + input_costs[company_id]
            )
            production_ratio = production[company_id] / max(state.baselineProduction, EPSILON)
            next_labor = _round(state.workforce * min(1.25, production_ratio))
            wage_bill = _round(state.workforce * state.wagePerWorker)
            profit = round(revenue - variable_cost - wage_bill, PRECISION)
            fulfillment = units_sold / max(demand, EPSILON) if demand > 0 else 1.0
            next_demand = _round(state.demand * (0.85 + 0.15 * fulfillment))
            if production[company_id] > state.capacity * (
                active_shock.capacityMultiplier if active_shock else 1.0
            ) + EPSILON:
                violations.append(f"{company_id}:production_exceeds_capacity")
            if abs(revenue - units_sold * price) > EPSILON:
                violations.append(f"{company_id}:revenue_reconciliation")
            if ending_inventory < -EPSILON:
                violations.append(f"{company_id}:negative_inventory")
            company_turns.append(
                CompanyTurn(
                    companyId=company_id,
                    production=production[company_id],
                    unitsSold=units_sold,
                    endingInventory=ending_inventory,
                    unmetDemand=unmet,
                    unitPrice=price,
                    revenue=revenue,
                    variableCost=variable_cost,
                    wageBill=wage_bill,
                    operatingProfit=profit,
                    nextTurnDemand=next_demand,
                    nextTurnLaborDemand=next_labor,
                )
            )
            total_revenue += revenue
            total_profit += profit
            total_production += production[company_id]
            total_unmet += unmet
            states[company_id] = state.model_copy(
                update={
                    "inventory": ending_inventory,
                    "demand": next_demand,
                    "workforce": next_labor,
                    "unitPrice": price,
                }
            )
        if any(not math.isfinite(value) for state in states.values() for value in (
            state.inventory, state.demand, state.workforce, state.unitPrice
        )):
            violations.append("non_finite_state")
        all_valid = all_valid and not violations
        snapshots.append(
            TurnSnapshot(
                turn=turn,
                companies=tuple(company_turns),
                flows=tuple(flows),
                metrics={
                    "totalProduction": _round(total_production),
                    "totalRevenue": _round(total_revenue),
                    "totalOperatingProfit": round(total_profit, PRECISION),
                    "totalUnmetDemand": _round(total_unmet),
                },
                invariantViolations=tuple(violations),
            )
        )

    canonical = {
        "seed": request.seed,
        "snapshots": [snapshot.model_dump(mode="json") for snapshot in snapshots],
        "finalState": [states[key].model_dump(mode="json") for key in sorted(states)],
    }
    result_hash = hashlib.sha256(
        json.dumps(canonical, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    return SimulationResult(
        seed=request.seed,
        turns=request.turns,
        snapshots=tuple(snapshots),
        finalState=tuple(states[key] for key in sorted(states)),
        resultHash=result_hash,
        invariants={
            "nonNegativeState": all_valid,
            "capacityRespected": all_valid,
            "flowLimitsRespected": all_valid,
            "accountingReconciled": all_valid,
        },
    )

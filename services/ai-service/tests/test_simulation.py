from fastapi.testclient import TestClient

from app.main import app
from app.simulation.engine import simulate
from app.simulation.models import SimulationRequest


def request(seed: int = 42, turns: int = 3) -> SimulationRequest:
    return SimulationRequest.model_validate(
        {
            "contractVersion": "v1",
            "seed": seed,
            "turns": turns,
            "companies": [
                {"companyId": "supplier", "industry": "materials", "baselineProduction": 80,
                 "capacity": 100, "inventory": 90, "demand": 40, "unitPrice": 5,
                 "unitVariableCost": 2, "workforce": 10, "wagePerWorker": 4, "productivity": 1},
                {"companyId": "maker", "industry": "electronics", "baselineProduction": 40,
                 "capacity": 50, "inventory": 5, "demand": 45, "unitPrice": 20,
                 "unitVariableCost": 7, "workforce": 20, "wagePerWorker": 5, "productivity": 1},
            ],
            "supplyLinks": [{"supplierId": "supplier", "customerId": "maker",
                             "inputUnitsPerOutput": 2, "maxFlow": 70, "unitCost": 4}],
            "shocks": [{"companyId": "supplier", "turn": 1, "supplyMultiplier": 0.5}],
        }
    )


def test_three_turn_run_is_deterministic_and_passes_invariants() -> None:
    first = simulate(request())
    second = simulate(request())
    assert first == second
    assert first.turns == 3
    assert len(first.snapshots) == 3
    assert all(first.invariants.values())
    assert all(not snapshot.invariantViolations for snapshot in first.snapshots)


def test_seed_changes_replay_but_never_capacity_or_flow_limits() -> None:
    first = simulate(request(seed=1))
    second = simulate(request(seed=2))
    assert first.resultHash != second.resultHash
    for snapshot in first.snapshots:
        assert snapshot.companies[1].production <= 50
        assert snapshot.flows[0].quantity <= 70


def test_shock_propagates_to_customer_without_negative_inventory() -> None:
    result = simulate(request())
    first_turn = result.snapshots[0]
    supplier = next(item for item in first_turn.companies if item.companyId == "supplier")
    maker = next(item for item in first_turn.companies if item.companyId == "maker")
    assert supplier.production < 80
    assert maker.production <= 35
    assert all(item.endingInventory >= 0 for item in first_turn.companies)


def test_http_contract_rejects_unknown_company_reference() -> None:
    client = TestClient(app)
    payload = request().model_dump(mode="json")
    payload["supplyLinks"][0]["supplierId"] = "missing"
    response = client.post("/api/v1/simulations/execute", json=payload)
    assert response.status_code == 422


def test_http_contract_executes_three_turn_replay() -> None:
    response = TestClient(app).post(
        "/api/v1/simulations/execute", json=request().model_dump(mode="json")
    )
    assert response.status_code == 200
    body = response.json()
    assert body["contractVersion"] == "v1"
    assert len(body["resultHash"]) == 64

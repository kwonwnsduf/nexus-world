import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def request(strategy: str = "NONE") -> dict[str, object]:
    return {
        "contractVersion": "v1",
        "simulationModel": "relationship-graph-v1",
        "seed": 42,
        "turns": 2,
        "nodes": [
            {"entityId": "tw", "entityType": "COUNTRY", "displayName": "Taiwan",
             "metrics": {"SUPPLY": 350, "DEMAND": 10}, "provenance": {"class": "OBSERVED"}},
            {"entityId": "kr", "entityType": "COUNTRY", "displayName": "Korea",
             "metrics": {"SUPPLY": 650, "DEMAND": 1000}, "provenance": {"class": "OBSERVED"}},
            {"entityId": "us", "entityType": "COUNTRY", "displayName": "United States",
             "metrics": {"SUPPLY": 200, "DEMAND": 20}, "provenance": {"class": "OBSERVED"}},
        ],
        "relationships": [
            {"relationshipId": "tw-kr", "relationshipType": "TRADE_FLOW",
             "sourceEntityId": "tw", "targetEntityId": "kr",
             "parameters": {"baselineFlow": 350, "dependencyRatio": 0.35},
             "provenance": {"baselineFlow": "OBSERVED", "dependencyRatio": "DERIVED"}},
            {"relationshipId": "us-kr", "relationshipType": "TRADE_FLOW",
             "sourceEntityId": "us", "targetEntityId": "kr",
             "parameters": {"baselineFlow": 200, "dependencyRatio": 0.2},
             "provenance": {"baselineFlow": "OBSERVED", "dependencyRatio": "DERIVED"}},
        ],
        "shocks": [{"entityId": "tw", "metric": "SUPPLY", "change": -0.5,
                    "turn": 1, "duration": 2, "basisType": "USER_ASSUMPTION"}],
        "strategy": strategy,
    }


def test_relationship_rule_propagates_only_with_derived_parameter() -> None:
    response = client.post("/api/v1/simulations/execute", json=request())
    assert response.status_code == 200
    value = response.json()
    korea = next(item for item in value["finalState"] if item["entityId"] == "kr")
    assert korea["metrics"]["SUPPLY"] == 536.25
    assert all(value["invariants"].values())


def test_observed_alternative_supply_reduces_propagated_loss() -> None:
    baseline = client.post("/api/v1/simulations/execute", json=request()).json()
    mitigated = client.post(
        "/api/v1/simulations/execute", json=request("OBSERVED_ALTERNATIVE_SUPPLY")
    ).json()
    base_kr = next(item for item in baseline["finalState"] if item["entityId"] == "kr")
    mitigated_kr = next(item for item in mitigated["finalState"] if item["entityId"] == "kr")
    assert mitigated_kr["metrics"]["SUPPLY"] > base_kr["metrics"]["SUPPLY"]


def test_missing_dependency_ratio_is_rejected_instead_of_defaulted() -> None:
    payload = request()
    payload["relationships"][0]["parameters"].pop("dependencyRatio")  # type: ignore[index]
    response = client.post("/api/v1/simulations/execute", json=payload)
    assert response.status_code == 422


@pytest.mark.parametrize(
    "relationship_type",
    ["TRADE_FLOW", "SUPPLIES", "DEPENDS_ON", "PRODUCES", "SHIPS_VIA"],
)
def test_each_supported_ontology_relationship_has_a_deterministic_rule(
    relationship_type: str,
) -> None:
    payload = request()
    relationships = payload["relationships"]
    assert isinstance(relationships, list)
    relationship = relationships[0]
    assert isinstance(relationship, dict)
    relationship["relationshipType"] = relationship_type
    payload["relationships"] = [relationship]
    first = client.post("/api/v1/simulations/execute", json=payload)
    second = client.post("/api/v1/simulations/execute", json=payload)
    assert first.status_code == 200
    assert first.json()["resultHash"] == second.json()["resultHash"]
    target = next(item for item in first.json()["finalState"] if item["entityId"] == "kr")
    assert target["metrics"]["SUPPLY"] < 650


def test_unknown_relationship_is_rejected_instead_of_silently_ignored() -> None:
    payload = request()
    payload["relationships"][0]["relationshipType"] = "UNKNOWN"  # type: ignore[index]
    assert client.post("/api/v1/simulations/execute", json=payload).status_code == 422

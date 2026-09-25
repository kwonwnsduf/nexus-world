from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_reports_service_identity() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/json")
    assert response.json() == {
        "status": "UP",
        "service": "ai-service",
        "version": "dev",
    }


def test_capabilities_match_v1_contract() -> None:
    response = client.get("/api/v1/platform/capabilities")

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "UP"
    assert payload["service"] == "ai-service"
    assert payload["contractVersion"] == "v1"
    assert payload["capabilities"] == [
        "retrieval",
        "agent-orchestration",
        "population-synthesis",
        "scenario-interpretation",
        "deterministic-simulation",
    ]

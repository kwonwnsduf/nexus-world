from __future__ import annotations

from fastapi.testclient import TestClient

from app.evals.graphrag import path_metrics
from app.graphrag.models import GraphNode, GraphPath, GraphRelationship
from app.graphrag.service import GraphRagService
from app.main import app
from app.retrieval.repository import InMemoryRetrievalRepository
from app.retrieval.service import RetrievalService


class FakeGraphRepository:
    def __init__(self) -> None:
        self.facility = GraphNode(
            "10000000-0000-0000-0000-000000000001",
            "FACILITY",
            "facility:fab",
            "Taiwan Fab",
            {"aliases": ["TSMC Fab", "대만 팹"]},
        )
        self.company = GraphNode(
            "10000000-0000-0000-0000-000000000002",
            "COMPANY",
            "company:chip",
            "Chip Company",
            {},
        )
        self.cohort = GraphNode(
            "10000000-0000-0000-0000-000000000003",
            "DEMOGRAPHIC_COHORT",
            "cohort:worker",
            "Factory Workers",
            {},
        )
        self.household = GraphNode(
            "10000000-0000-0000-0000-000000000004",
            "HOUSEHOLD_ARCHETYPE",
            "household:worker",
            "Worker Households",
            {},
        )

    def match_nodes(
        self, world_version_id: str, query: str, limit: int, authorization: str | None
    ) -> list[GraphNode]:
        _ = world_version_id, limit, authorization
        return [self.facility] if "tsmc fab" in query.casefold() or "대만 팹" in query else []

    def paths(
        self,
        world_version_id: str,
        root_entity_id: str,
        max_depth: int,
        authorization: str | None,
    ) -> list[GraphPath]:
        _ = world_version_id, root_entity_id, max_depth, authorization
        evidence = {
            "evidenceId": "20000000-0000-0000-0000-000000000001",
            "dataSourceId": "30000000-0000-0000-0000-000000000001",
            "sourceUri": "https://example.test/filing",
        }
        edges = (
            GraphRelationship(
                "40000000-0000-0000-0000-000000000001",
                "OPERATES",
                self.company.id,
                self.facility.id,
                evidence,
            ),
            GraphRelationship(
                "40000000-0000-0000-0000-000000000002",
                "EMPLOYS",
                self.company.id,
                self.cohort.id,
                {},
            ),
            GraphRelationship(
                "40000000-0000-0000-0000-000000000003",
                "MEMBER_PROFILE_OF",
                self.cohort.id,
                self.household.id,
                {},
            ),
        )
        return [GraphPath((self.facility, self.company, self.cohort, self.household), edges)]


def test_alias_to_industrial_society_path_is_ranked_and_grounded() -> None:
    service = GraphRagService(
        FakeGraphRepository(), RetrievalService(InMemoryRetrievalRepository())
    )

    result = service.query(
        "00000000-0000-0000-0000-000000000001",
        "대만 팹 고용과 노동자 가구 영향",
    )

    assert result.roots[0].display_name == "Taiwan Fab"
    assert [edge.relationship_type for edge in result.paths[0].path.relationships] == [
        "OPERATES",
        "EMPLOYS",
        "MEMBER_PROFILE_OF",
    ]
    assert result.evidence[0].source_uri == "https://example.test/filing"
    assert "Worker Households" in result.answer


def test_unknown_alias_returns_safe_empty_graph_answer() -> None:
    service = GraphRagService(
        FakeGraphRepository(), RetrievalService(InMemoryRetrievalRepository())
    )
    result = service.query("00000000-0000-0000-0000-000000000001", "unknown")
    assert result.roots == ()
    assert result.paths == ()
    assert result.answer.startswith("No graph entity alias matched")


def test_path_f1_uses_exact_relationship_sequences() -> None:
    metrics = path_metrics(
        {("OPERATES", "EMPLOYS"), ("SUPPLIES",)},
        [("OPERATES", "EMPLOYS"), ("LOCATED_IN",)],
    )
    assert metrics == {"precision": 0.5, "recall": 0.5, "f1": 0.5}


def test_graphrag_api_requires_bearer_token_before_graph_access() -> None:
    response = TestClient(app).post(
        "/api/v1/graphrag/query",
        json={
            "worldVersionId": "00000000-0000-0000-0000-000000000001",
            "query": "대만 팹 영향",
        },
    )
    assert response.status_code == 401

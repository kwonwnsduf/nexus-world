from __future__ import annotations

from typing import Any, Protocol

import httpx

from app.graphrag.models import GraphNode, GraphPath, GraphRelationship


class GraphRepositoryError(RuntimeError):
    def __init__(self, message: str, status_code: int = 503) -> None:
        super().__init__(message)
        self.status_code = status_code


class GraphRepository(Protocol):
    def match_nodes(
        self, world_version_id: str, query: str, limit: int, authorization: str | None
    ) -> list[GraphNode]: ...

    def paths(
        self,
        world_version_id: str,
        root_entity_id: str,
        max_depth: int,
        authorization: str | None,
    ) -> list[GraphPath]: ...


class CoreApiGraphRepository:
    def __init__(
        self,
        base_url: str,
        *,
        timeout_seconds: float = 5.0,
        client: httpx.Client | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.client = client

    def match_nodes(
        self, world_version_id: str, query: str, limit: int, authorization: str | None
    ) -> list[GraphNode]:
        payload = self._get(
            f"/api/v1/world-versions/{world_version_id}/graph/entities/matches",
            {"query": query, "limit": limit},
            authorization,
        )
        return [self._node(row) for row in payload.get("matches", [])]

    def paths(
        self,
        world_version_id: str,
        root_entity_id: str,
        max_depth: int,
        authorization: str | None,
    ) -> list[GraphPath]:
        payload = self._get(
            f"/api/v1/world-versions/{world_version_id}/graph/paths/{root_entity_id}",
            {"maxDepth": max_depth},
            authorization,
        )
        return [
            GraphPath(
                tuple(self._node(node) for node in row.get("nodes", [])),
                tuple(
                    GraphRelationship(
                        id=str(edge["id"]),
                        relationship_type=str(edge["relationshipType"]),
                        source_entity_id=str(edge["sourceEntityId"]),
                        target_entity_id=str(edge["targetEntityId"]),
                        attributes=dict(edge.get("attributes") or {}),
                    )
                    for edge in row.get("relationships", [])
                ),
            )
            for row in payload.get("paths", [])
        ]

    def _get(
        self, path: str, params: dict[str, str | int], authorization: str | None
    ) -> dict[str, Any]:
        owns_client = self.client is None
        client = self.client or httpx.Client(timeout=self.timeout_seconds)
        try:
            headers = {"Authorization": authorization} if authorization else {}
            response = client.get(f"{self.base_url}{path}", params=params, headers=headers)
            if response.status_code in (401, 403):
                raise GraphRepositoryError(
                    "Core API rejected the GraphRAG credentials", response.status_code
                )
            if 400 <= response.status_code < 500:
                raise GraphRepositoryError(
                    "Core API rejected the graph query", response.status_code
                )
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload, dict):
                raise GraphRepositoryError("Core API graph response was not an object")
            return payload
        except GraphRepositoryError:
            raise
        except (httpx.HTTPError, ValueError) as error:
            raise GraphRepositoryError("Core API graph query failed") from error
        finally:
            if owns_client:
                client.close()

    @staticmethod
    def _node(row: object) -> GraphNode:
        if not isinstance(row, dict):
            raise GraphRepositoryError("Core API returned a malformed graph node")
        return GraphNode(
            id=str(row["id"]),
            entity_type=str(row["entityType"]),
            natural_key=str(row["naturalKey"]),
            display_name=str(row["displayName"]),
            attributes=dict(row.get("attributes") or {}),
        )

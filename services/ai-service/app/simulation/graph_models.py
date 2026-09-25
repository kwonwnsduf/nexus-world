from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

RelationshipType = Literal["TRADE_FLOW", "SUPPLIES", "DEPENDS_ON", "PRODUCES", "SHIPS_VIA"]


class GraphNode(BaseModel):
    model_config = ConfigDict(extra="forbid")
    entityId: str = Field(min_length=1, max_length=200)
    entityType: str = Field(min_length=1, max_length=48)
    displayName: str = Field(min_length=1, max_length=240)
    metrics: dict[str, float]
    provenance: dict[str, Any]


class GraphRelationship(BaseModel):
    model_config = ConfigDict(extra="forbid")
    relationshipId: str = Field(min_length=1, max_length=200)
    relationshipType: RelationshipType
    sourceEntityId: str
    targetEntityId: str
    parameters: dict[str, float]
    provenance: dict[str, Any]


class GraphShock(BaseModel):
    model_config = ConfigDict(extra="forbid")
    entityId: str
    metric: str
    change: float = Field(ge=-1, le=1)
    turn: int = Field(default=1, ge=1, le=120)
    duration: int | None = Field(default=None, ge=1, le=120)
    basisType: Literal["USER_ASSUMPTION"]


class GraphSimulationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    contractVersion: Literal["v1"]
    simulationModel: Literal["relationship-graph-v1"]
    seed: int
    turns: int = Field(ge=1, le=120)
    nodes: tuple[GraphNode, ...] = Field(min_length=1, max_length=5_000)
    relationships: tuple[GraphRelationship, ...] = Field(max_length=20_000)
    shocks: tuple[GraphShock, ...] = Field(max_length=20_000)
    strategy: Literal["NONE", "OBSERVED_ALTERNATIVE_SUPPLY"] = "NONE"
    traversalMode: Literal["WORLD_VERSION", "NEO4J_PATHS"] = "WORLD_VERSION"
    maxPropagationDepth: int = Field(default=120, ge=1, le=120)

    @model_validator(mode="after")
    def validate_graph(self) -> GraphSimulationRequest:
        ids = [node.entityId for node in self.nodes]
        if len(ids) != len(set(ids)):
            raise ValueError("graph node ids must be unique")
        known = set(ids)
        for edge in self.relationships:
            if edge.sourceEntityId not in known or edge.targetEntityId not in known:
                raise ValueError("relationship endpoints must reference known nodes")
            ratio = edge.parameters.get("dependencyRatio")
            if ratio is None or not 0 <= ratio <= 1:
                raise ValueError("quantitative relationship requires dependencyRatio")
        if any(shock.entityId not in known for shock in self.shocks):
            raise ValueError("shock target must reference a known node")
        return self

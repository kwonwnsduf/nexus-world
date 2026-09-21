from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class GraphNode:
    id: str
    entity_type: str
    natural_key: str
    display_name: str
    attributes: dict[str, Any]


@dataclass(frozen=True)
class GraphRelationship:
    id: str
    relationship_type: str
    source_entity_id: str
    target_entity_id: str
    attributes: dict[str, Any]


@dataclass(frozen=True)
class GraphPath:
    nodes: tuple[GraphNode, ...]
    relationships: tuple[GraphRelationship, ...]


@dataclass(frozen=True)
class RankedPath:
    path: GraphPath
    score: float


@dataclass(frozen=True)
class EvidenceReference:
    evidence_id: str | None
    data_source_id: str | None
    source_uri: str | None
    owner_kind: str
    owner_id: str


@dataclass(frozen=True)
class GraphRagResult:
    roots: tuple[GraphNode, ...]
    paths: tuple[RankedPath, ...]
    evidence: tuple[EvidenceReference, ...]
    text_hits: tuple[Any, ...]
    answer: str

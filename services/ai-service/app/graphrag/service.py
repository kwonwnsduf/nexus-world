from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable
from typing import Any
from uuid import UUID

from app.graphrag.models import (
    EvidenceReference,
    GraphNode,
    GraphPath,
    GraphRagResult,
    RankedPath,
)
from app.graphrag.repository import GraphRepository
from app.retrieval.embedding import EmbeddingConfigurationError, EmbeddingServiceError
from app.retrieval.models import SearchMode
from app.retrieval.service import RetrievalService


class GraphRagService:
    def __init__(self, graph: GraphRepository, retrieval: RetrievalService) -> None:
        self.graph = graph
        self.retrieval = retrieval

    def query(
        self,
        world_version_id: str,
        query: str,
        *,
        max_depth: int = 3,
        root_limit: int = 5,
        path_limit: int = 10,
        evidence_limit: int = 5,
        authorization: str | None = None,
    ) -> GraphRagResult:
        roots = self.graph.match_nodes(world_version_id, query, root_limit, authorization)
        candidates: list[GraphPath] = []
        for root in roots:
            candidates.extend(
                self.graph.paths(world_version_id, root.id, max_depth, authorization)
            )

        ranked = self._rank_paths(query, candidates)[:path_limit]
        evidence = self._collect_evidence(item.path for item in ranked)
        expansion = self._expansion(query, roots, ranked)
        # Graph terms are useful lexical anchors, while the original question often
        # carries semantic intent that is not an exact token match. Fuse both signals.
        # A missing/temporarily unavailable embedding provider must not make graph
        # evidence unusable, so keyword retrieval remains the deterministic fallback.
        try:
            text_hits = tuple(
                self.retrieval.search(
                    expansion, SearchMode.HYBRID, evidence_limit, rerank=True
                )
            )
        except (EmbeddingConfigurationError, EmbeddingServiceError):
            text_hits = tuple(
                self.retrieval.search(
                    expansion, SearchMode.KEYWORD, evidence_limit, rerank=True
                )
            )
        return GraphRagResult(
            roots=tuple(roots),
            paths=tuple(ranked),
            evidence=tuple(evidence),
            text_hits=text_hits,
            answer=self._answer(roots, ranked, evidence, text_hits),
        )

    def _rank_paths(self, query: str, paths: list[GraphPath]) -> list[RankedPath]:
        query_terms = self._tokens(query)
        unique: dict[tuple[tuple[str, ...], tuple[str, ...]], GraphPath] = {}
        for path in paths:
            signature = (
                tuple(node.id for node in path.nodes),
                tuple(edge.id for edge in path.relationships),
            )
            unique[signature] = path
        ranked: list[RankedPath] = []
        for path in unique.values():
            searchable = " ".join(
                [value for node in path.nodes for value in (node.display_name, node.natural_key)]
                + [edge.relationship_type for edge in path.relationships]
            )
            overlap = len(query_terms.intersection(self._tokens(searchable))) / max(
                1, len(query_terms)
            )
            grounded = 0.15 if self._collect_evidence((path,)) else 0.0
            score = overlap + grounded + (0.05 / max(1, len(path.relationships)))
            ranked.append(RankedPath(path, round(score, 6)))
        return sorted(
            ranked,
            key=lambda item: (
                -item.score,
                len(item.path.relationships),
                tuple(node.id for node in item.path.nodes),
            ),
        )

    def _collect_evidence(self, paths: Iterable[GraphPath]) -> list[EvidenceReference]:
        unique: dict[tuple[str | None, str | None, str | None, str, str], EvidenceReference] = {}
        for path in paths:
            owners: list[tuple[str, str, dict[str, Any]]] = [
                ("NODE", node.id, node.attributes) for node in path.nodes
            ] + [
                ("RELATIONSHIP", edge.id, edge.attributes) for edge in path.relationships
            ]
            for owner_kind, owner_id, attributes in owners:
                for values in self._reference_maps(attributes):
                    reference = EvidenceReference(
                        self._uuid_string(values.get("evidenceId")),
                        self._uuid_string(values.get("dataSourceId")),
                        self._string(values.get("sourceUri")),
                        owner_kind,
                        owner_id,
                    )
                    if any((reference.evidence_id, reference.data_source_id, reference.source_uri)):
                        key = (
                            reference.evidence_id,
                            reference.data_source_id,
                            reference.source_uri,
                            reference.owner_kind,
                            reference.owner_id,
                        )
                        unique[key] = reference
        return list(unique.values())

    def _reference_maps(self, value: object) -> Iterable[dict[str, Any]]:
        if isinstance(value, dict):
            if {"evidenceId", "dataSourceId", "sourceUri"}.intersection(value):
                yield value
            for child in value.values():
                yield from self._reference_maps(child)
        elif isinstance(value, list):
            for child in value:
                yield from self._reference_maps(child)

    @staticmethod
    def _expansion(query: str, roots: list[GraphNode], paths: list[RankedPath]) -> str:
        terms = [query]
        terms.extend(root.display_name for root in roots)
        for ranked in paths[:3]:
            terms.extend(node.display_name for node in ranked.path.nodes)
            terms.extend(edge.relationship_type for edge in ranked.path.relationships)
        return " ".join(dict.fromkeys(terms))[:2_000]

    @staticmethod
    def _answer(
        roots: list[GraphNode],
        paths: list[RankedPath],
        evidence: list[EvidenceReference],
        text_hits: tuple[Any, ...],
    ) -> str:
        if not roots:
            return "No graph entity alias matched the query in this world version."
        if not paths:
            return f"Matched {roots[0].display_name}, but no bounded graph path was found."
        best = paths[0].path
        chain: list[str] = []
        for index, node in enumerate(best.nodes):
            chain.append(node.display_name)
            if index < len(best.relationships):
                chain.append(f"--{best.relationships[index].relationship_type}--")
        grounding = f" {len(evidence)} graph evidence reference(s)"
        if text_hits:
            grounding += f" and {len(text_hits)} indexed text citation(s)"
        return "Best supported path: " + " ".join(chain) + ". Grounded by" + grounding + "."

    @staticmethod
    def _tokens(value: str) -> set[str]:
        normalized = unicodedata.normalize("NFKC", value).casefold()
        return set(re.findall(r"[\w가-힣]+", normalized))

    @staticmethod
    def _string(value: object) -> str | None:
        return str(value) if value is not None and str(value).strip() else None

    @staticmethod
    def _uuid_string(value: object) -> str | None:
        if value is None:
            return None
        try:
            return str(UUID(str(value)))
        except ValueError:
            return None

from __future__ import annotations

import argparse
import hashlib
import json
import math
import time
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx

from app.retrieval.models import Document, SearchMode
from app.retrieval.normalization import SimpleSearchTextNormalizer, lexical_tokens
from app.retrieval.repository import InMemoryRetrievalRepository
from app.retrieval.service import RetrievalService


@dataclass(frozen=True)
class EvaluationQuery:
    query_id: str
    text: str
    relevant_document_ids: frozenset[str]


class LegacyHashEmbedding:
    """Frozen Day 17 baseline, available only to the evaluation harness."""

    model = "legacy-feature-hashing"
    dimensions = 384

    def embed(self, value: str) -> tuple[float, ...]:
        return self.embed_many([value])[0]

    def embed_many(self, values: Sequence[str]) -> list[tuple[float, ...]]:
        return [self._one(value) for value in values]

    def _one(self, value: str) -> tuple[float, ...]:
        vector = [0.0] * self.dimensions
        for position, term in enumerate(lexical_tokens(value)):
            digest = hashlib.blake2b(term.encode("utf-8"), digest_size=8).digest()
            bucket = int.from_bytes(digest[:4], "big") % self.dimensions
            vector[bucket] += (1.0 if digest[4] & 1 else -1.0) / math.sqrt(position + 1)
        magnitude = math.sqrt(sum(component * component for component in vector)) or 1.0
        return tuple(component / magnitude for component in vector)


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    index = max(0, math.ceil(fraction * len(ordered)) - 1)
    return ordered[index]


def metrics(
    ranked_results: list[tuple[EvaluationQuery, list[str], float]], k: int
) -> dict[str, float]:
    recalls: list[float] = []
    reciprocal_ranks: list[float] = []
    latencies = [latency for _, _, latency in ranked_results]
    for query, ranked, _ in ranked_results:
        top_k = ranked[:k]
        recalls.append(
            len(query.relevant_document_ids.intersection(top_k))
            / len(query.relevant_document_ids)
        )
        rank = next(
            (
                index
                for index, document_id in enumerate(ranked, start=1)
                if document_id in query.relevant_document_ids
            ),
            None,
        )
        reciprocal_ranks.append(0.0 if rank is None else 1.0 / rank)
    return {
        f"recall@{k}": sum(recalls) / len(recalls),
        "mrr": sum(reciprocal_ranks) / len(reciprocal_ranks),
        "latencyP50Ms": percentile(latencies, 0.50),
        "latencyP95Ms": percentile(latencies, 0.95),
    }


def load_dataset(path: Path) -> tuple[list[dict[str, Any]], list[EvaluationQuery]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    queries = [
        EvaluationQuery(
            query_id=row["queryId"],
            text=row["text"],
            relevant_document_ids=frozenset(row["relevantDocumentIds"]),
        )
        for row in payload["queries"]
    ]
    return list(payload["documents"]), queries


def unique_documents(hits: Sequence[dict[str, Any]]) -> list[str]:
    ordered: list[str] = []
    for hit in hits:
        document_id = str(hit["chunk"]["documentId"])
        if document_id not in ordered:
            ordered.append(document_id)
    return ordered


def evaluate_api(
    base_url: str,
    documents: list[dict[str, Any]],
    queries: list[EvaluationQuery],
    k: int,
    repeats: int,
) -> dict[str, Any]:
    strategies = {
        "ftsOnly": ("keyword", False),
        "vectorOnly": ("vector", False),
        "hybridRrf": ("hybrid", False),
        "hybridReranked": ("hybrid", True),
    }
    output: dict[str, Any] = {}
    with httpx.Client(base_url=base_url, timeout=120.0) as client:
        for document in documents:
            response = client.post("/api/v1/retrieval/documents", json=document)
            response.raise_for_status()
        for strategy, (mode, rerank) in strategies.items():
            warmup = client.post(
                "/api/v1/retrieval/search",
                json={"query": queries[0].text, "mode": mode, "limit": k, "rerank": rerank},
            )
            warmup.raise_for_status()
            rows: list[tuple[EvaluationQuery, list[str], float]] = []
            for query in queries:
                ranked: list[str] = []
                for _ in range(repeats):
                    started = time.perf_counter()
                    response = client.post(
                        "/api/v1/retrieval/search",
                        json={"query": query.text, "mode": mode, "limit": k, "rerank": rerank},
                    )
                    response.raise_for_status()
                    latency = (time.perf_counter() - started) * 1_000
                    ranked = unique_documents(response.json()["hits"])
                    rows.append((query, ranked, latency))
            output[strategy] = metrics(rows, k)
    return output


def evaluate_legacy(
    documents: list[dict[str, Any]], queries: list[EvaluationQuery], k: int, repeats: int
) -> dict[str, Any]:
    service = RetrievalService(
        InMemoryRetrievalRepository(),
        embedder=LegacyHashEmbedding(),
        normalizer=SimpleSearchTextNormalizer(),
    )
    for row in documents:
        service.ingest(
            Document(
                document_id=row["documentId"],
                title=row["title"],
                content=row["content"],
                source_uri=row.get("sourceUri"),
            )
        )
    strategies = {
        "ftsOnly": (SearchMode.KEYWORD, False),
        "vectorOnly": (SearchMode.VECTOR, False),
        "hybridRrf": (SearchMode.HYBRID, False),
        "hybridReranked": (SearchMode.HYBRID, True),
    }
    output: dict[str, Any] = {}
    for name, (mode, rerank) in strategies.items():
        rows: list[tuple[EvaluationQuery, list[str], float]] = []
        for query in queries:
            for _ in range(repeats):
                started = time.perf_counter()
                hits = service.search(query.text, mode, k, rerank=rerank)
                latency = (time.perf_counter() - started) * 1_000
                ranked: list[str] = []
                for hit in hits:
                    if hit.chunk.document_id not in ranked:
                        ranked.append(hit.chunk.document_id)
                rows.append((query, ranked, latency))
        output[name] = metrics(rows, k)
    return output


def comparison(current: dict[str, Any], baseline: dict[str, Any]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for strategy, current_metrics in current.items():
        old_metrics = baseline.get(strategy, {})
        result[strategy] = {
            key: float(value) - float(old_metrics.get(key, 0.0))
            for key, value in current_metrics.items()
        }
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate NEXUS WORLD retrieval")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--k", type=int, default=5)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--skip-legacy", action="store_true")
    args = parser.parse_args()

    documents, queries = load_dataset(args.dataset)
    current = evaluate_api(args.base_url, documents, queries, args.k, args.repeats)
    payload: dict[str, Any] = {
        "dataset": str(args.dataset),
        "queryCount": len(queries),
        "k": args.k,
        "repeats": args.repeats,
        "current": {
            "ftsAnalyzer": "kiwi-v1",
            "embeddingProvider": "openai:text-embedding-3-small:1536",
            "strategies": current,
        },
    }
    if not args.skip_legacy:
        legacy = evaluate_legacy(documents, queries, args.k, args.repeats)
        payload["legacyBaseline"] = {
            "ftsAnalyzer": "simple-v1",
            "embeddingProvider": "feature-hashing:384",
            "latencyScope": "in-process; do not compare directly with HTTP latency",
            "strategies": legacy,
        }
        payload["qualityDeltaCurrentMinusLegacy"] = comparison(current, legacy)

    rendered = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()

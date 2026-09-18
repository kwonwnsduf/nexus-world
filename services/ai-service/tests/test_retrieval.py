from __future__ import annotations

import math
import threading
from collections.abc import Sequence
from concurrent.futures import ThreadPoolExecutor
from typing import Any

from fastapi.testclient import TestClient

import app.api.retrieval as retrieval_api
from app.main import app
from app.retrieval.models import Document, SearchMode
from app.retrieval.normalization import KiwiSearchTextNormalizer, SimpleSearchTextNormalizer
from app.retrieval.repository import InMemoryRetrievalRepository
from app.retrieval.service import RetrievalService
from app.retrieval.text import SectionAwareChunker


class CountingEmbedding:
    model = "test-semantic-embedding"
    dimensions = 32

    def __init__(self) -> None:
        self.batch_calls = 0
        self._guard = threading.Lock()

    def embed(self, value: str) -> tuple[float, ...]:
        return self.embed_many([value])[0]

    def embed_many(self, values: Sequence[str]) -> list[tuple[float, ...]]:
        with self._guard:
            self.batch_calls += 1
        results: list[tuple[float, ...]] = []
        for value in values:
            vector = [0.0] * self.dimensions
            for term in value.casefold().split():
                vector[sum(term.encode("utf-8")) % self.dimensions] += 1.0
            magnitude = math.sqrt(sum(component * component for component in vector)) or 1.0
            results.append(tuple(component / magnitude for component in vector))
        return results


def service(embedder: CountingEmbedding | None = None) -> RetrievalService:
    return RetrievalService(
        InMemoryRetrievalRepository(),
        embedder=embedder or CountingEmbedding(),
        normalizer=SimpleSearchTextNormalizer(),
    )


def test_section_aware_chunking_is_deterministic_and_preserves_locators() -> None:
    content = "# Supply shock\nTaiwan output fell sharply.\n## Employment\nKorean hiring slowed."
    document = Document("report-1", "Risk report", content)
    chunker = SectionAwareChunker(target_characters=200, overlap_characters=20)

    first = chunker.chunk(document)
    second = chunker.chunk(document)

    assert first == second
    assert [chunk.section_path for chunk in first] == [
        ("Supply shock",),
        ("Supply shock", "Employment"),
    ]
    assert all(
        content[chunk.start_character : chunk.end_character] == chunk.content for chunk in first
    )


def test_kiwi_normalizes_korean_and_preserves_english_numbers_and_codes() -> None:
    normalized = KiwiSearchTextNormalizer().normalize(
        "삼성전자가 반도체 사업에서 매출을 크게 늘렸다. HBM3E와 HS-8542는 2026년 증가했다."
    )

    assert "삼성전자" in normalized
    assert "반도체" in normalized
    assert "사업" in normalized
    assert "매출" in normalized
    assert "늘리" in normalized
    assert "hbm" in normalized
    assert "8542" in normalized
    assert "2026" in normalized


def test_hybrid_rrf_and_reranker_improve_exact_evidence_order() -> None:
    retrieval = service()
    retrieval.ingest(
        Document(
            "policy-1",
            "Policy note",
            "# Employment support\nRetraining grants reduce semiconductor unemployment.",
        )
    )
    retrieval.ingest(
        Document(
            "logistics-1",
            "Port note",
            "# Logistics\nSemiconductor shipments use alternative ports.",
        )
    )

    hits = retrieval.search("semiconductor unemployment", SearchMode.HYBRID, 2)

    assert hits[0].chunk.document_id == "policy-1"
    assert hits[0].keyword_rank is not None
    assert hits[0].vector_rank is not None


def test_keyword_only_does_not_call_embedding_api() -> None:
    embedder = CountingEmbedding()
    retrieval = service(embedder)
    retrieval.ingest(Document("korean-1", "산업", "반도체 사업 매출이 증가했다."))
    calls_after_ingest = embedder.batch_calls

    hits = retrieval.search("반도체 매출", SearchMode.KEYWORD, rerank=False)

    assert hits[0].chunk.document_id == "korean-1"
    assert embedder.batch_calls == calls_after_ingest


def test_same_document_concurrent_indexing_embeds_once_and_is_idempotent() -> None:
    embedder = CountingEmbedding()
    retrieval = service(embedder)
    document = Document("same-document", "Report", "# Risk\nSupply risk increased.")

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(retrieval.ingest, (document, document)))

    assert embedder.batch_calls == 1
    assert results[0] == results[1]


def test_answer_contains_resolvable_citation_markers() -> None:
    retrieval = service()
    retrieval.ingest(
        Document(
            "fiscal-1",
            "Fiscal brief",
            "# Welfare\nExpanded transfers increased household disposable income.",
        )
    )

    answer, citations = retrieval.answer("What increased household disposable income?")

    assert "[1]" in answer
    assert citations[0].chunk.document_id == "fiscal-1"


def test_retrieval_api_remains_compatible_and_returns_provenance(
    monkeypatch: Any,
) -> None:
    monkeypatch.setattr(retrieval_api, "service", service())
    client = TestClient(app)
    ingest = client.post(
        "/api/v1/retrieval/documents",
        json={
            "documentId": "api-report-1",
            "title": "Industry report",
            "content": (
                "# Production\n"
                "The earthquake reduced semiconductor production by 50 percent."
            ),
            "sourceUri": "https://example.test/report",
        },
    )
    assert ingest.status_code == 201
    assert ingest.json()["chunkCount"] == 1

    search = client.post(
        "/api/v1/retrieval/search",
        json={"query": "semiconductor production", "mode": "hybrid", "limit": 5},
    )
    assert search.status_code == 200
    assert search.json()["hits"][0]["chunk"]["documentId"] == "api-report-1"
    assert search.json()["reranked"] is True

    answer = client.post(
        "/api/v1/retrieval/answer",
        json={"query": "What reduced semiconductor production?"},
    )
    assert answer.status_code == 200
    assert answer.json()["citations"][0]["documentId"] == "api-report-1"
    assert answer.json()["citations"][0]["sourceUri"] == "https://example.test/report"

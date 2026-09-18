from __future__ import annotations

import math
import threading
from collections import Counter
from collections.abc import Iterator
from contextlib import AbstractContextManager, contextmanager
from typing import Protocol

from app.retrieval.models import Chunk
from app.retrieval.normalization import lexical_tokens


class RetrievalRepository(Protocol):
    def document_lock(self, document_id: str) -> AbstractContextManager[None]: ...

    def current_document(self, document_id: str, fingerprint: str) -> list[Chunk] | None: ...

    def replace_document(self, chunks: list[Chunk], fingerprint: str) -> None: ...

    def keyword_search(self, query: str, limit: int) -> list[tuple[Chunk, float]]: ...

    def vector_search(
        self, embedding: tuple[float, ...], limit: int
    ) -> list[tuple[Chunk, float]]: ...


class InMemoryRetrievalRepository:
    def __init__(self) -> None:
        self._chunks: dict[str, Chunk] = {}
        self._fingerprints: dict[str, str] = {}
        self._locks: dict[str, threading.Lock] = {}
        self._locks_guard = threading.Lock()

    @contextmanager
    def document_lock(self, document_id: str) -> Iterator[None]:
        with self._locks_guard:
            lock = self._locks.setdefault(document_id, threading.Lock())
        with lock:
            yield

    def current_document(self, document_id: str, fingerprint: str) -> list[Chunk] | None:
        if self._fingerprints.get(document_id) != fingerprint:
            return None
        return sorted(
            (chunk for chunk in self._chunks.values() if chunk.document_id == document_id),
            key=lambda chunk: chunk.ordinal,
        )

    def replace_document(self, chunks: list[Chunk], fingerprint: str) -> None:
        if not chunks:
            return
        document_id = chunks[0].document_id
        self._chunks = {
            key: value for key, value in self._chunks.items() if value.document_id != document_id
        }
        self._chunks.update({chunk.chunk_id: chunk for chunk in chunks})
        self._fingerprints[document_id] = fingerprint

    def keyword_search(self, query: str, limit: int) -> list[tuple[Chunk, float]]:
        query_terms = Counter(lexical_tokens(query))
        scored: list[tuple[Chunk, float]] = []
        for chunk in self._chunks.values():
            haystack = Counter(lexical_tokens(chunk.search_text))
            score = sum(min(count, haystack[term]) for term, count in query_terms.items())
            if score:
                scored.append((chunk, float(score) / math.sqrt(max(1, sum(haystack.values())))))
        return sorted(scored, key=lambda item: (-item[1], item[0].chunk_id))[:limit]

    def vector_search(
        self, embedding: tuple[float, ...], limit: int
    ) -> list[tuple[Chunk, float]]:
        scored = [
            (chunk, sum(a * b for a, b in zip(embedding, chunk.embedding, strict=True)))
            for chunk in self._chunks.values()
            if chunk.embedding
        ]
        return sorted(scored, key=lambda item: (-item[1], item[0].chunk_id))[:limit]

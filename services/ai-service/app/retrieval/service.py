from __future__ import annotations

import hashlib
import json
import re
from collections import defaultdict
from dataclasses import replace

from app.retrieval.embedding import EmbeddingProvider, OpenAIEmbedding
from app.retrieval.models import Chunk, Document, SearchHit, SearchMode
from app.retrieval.normalization import (
    KiwiSearchTextNormalizer,
    SearchTextNormalizer,
    lexical_tokens,
    normalize_unicode,
)
from app.retrieval.repository import RetrievalRepository
from app.retrieval.text import SectionAwareChunker


class RetrievalService:
    def __init__(
        self,
        repository: RetrievalRepository,
        chunker: SectionAwareChunker | None = None,
        embedder: EmbeddingProvider | None = None,
        normalizer: SearchTextNormalizer | None = None,
    ) -> None:
        self.repository = repository
        self.chunker = chunker or SectionAwareChunker()
        self.embedder = embedder or OpenAIEmbedding()
        self.normalizer = normalizer or KiwiSearchTextNormalizer()

    def ingest(self, document: Document) -> list[Chunk]:
        fingerprint = self._fingerprint(document)
        with self.repository.document_lock(document.document_id):
            current = self.repository.current_document(document.document_id, fingerprint)
            if current is not None:
                return current
            parsed_chunks = self.chunker.chunk(document)
            if not parsed_chunks:
                raise ValueError("document content must not be blank")
            embedding_inputs = [
                " > ".join((*chunk.section_path, chunk.content)) for chunk in parsed_chunks
            ]
            embeddings = self.embedder.embed_many(embedding_inputs)
            chunks = [
                replace(
                    chunk,
                    search_text=self.normalizer.normalize(
                        " ".join((chunk.title, *chunk.section_path, chunk.content))
                    ),
                    embedding=embedding,
                    embedding_model=self.embedder.model,
                )
                for chunk, embedding in zip(parsed_chunks, embeddings, strict=True)
            ]
            self.repository.replace_document(chunks, fingerprint)
            return chunks

    def search(
        self,
        query: str,
        mode: SearchMode,
        limit: int = 5,
        *,
        rerank: bool = True,
    ) -> list[SearchHit]:
        if not query.strip():
            raise ValueError("query must not be blank")
        candidate_limit = min(max(limit * 4, 20), 100)
        normalized_query = self.normalizer.normalize(query)
        if mode == SearchMode.KEYWORD:
            keyword = self.repository.keyword_search(normalized_query, candidate_limit)
            hits = self._single(keyword, "keyword")
            return self._rerank(query, hits, limit) if rerank else hits[:limit]
        if mode == SearchMode.VECTOR:
            vector = self.repository.vector_search(self.embedder.embed(query), candidate_limit)
            hits = self._single(vector, "vector")
            return self._rerank(query, hits, limit) if rerank else hits[:limit]
        keyword = self.repository.keyword_search(normalized_query, candidate_limit)
        vector = self.repository.vector_search(self.embedder.embed(query), candidate_limit)
        hits = self._rrf(keyword, vector)
        return self._rerank(query, hits, limit) if rerank else hits[:limit]

    def answer(self, query: str, limit: int = 5) -> tuple[str, list[SearchHit]]:
        hits = self.search(query, SearchMode.HYBRID, limit)
        if not hits or hits[0].score <= 0:
            return "The indexed evidence does not support an answer.", []
        query_terms = set(lexical_tokens(query))
        statements: list[str] = []
        for index, hit in enumerate(hits[:3], start=1):
            sentences = re.split(r"(?<=[.!?。！？])\s+", hit.chunk.content)
            best = max(
                sentences,
                key=lambda sentence: len(query_terms.intersection(lexical_tokens(sentence))),
                default=hit.chunk.content,
            ).strip()
            if best:
                statements.append(f"{best} [{index}]")
        return " ".join(statements), hits

    @staticmethod
    def _single(items: list[tuple[Chunk, float]], source: str) -> list[SearchHit]:
        return [
            SearchHit(
                chunk=chunk,
                score=score,
                keyword_rank=rank if source == "keyword" else None,
                vector_rank=rank if source == "vector" else None,
            )
            for rank, (chunk, score) in enumerate(items, start=1)
        ]

    @staticmethod
    def _rrf(
        keyword: list[tuple[Chunk, float]], vector: list[tuple[Chunk, float]], k: int = 60
    ) -> list[SearchHit]:
        scores: dict[str, float] = defaultdict(float)
        chunks: dict[str, Chunk] = {}
        keyword_ranks: dict[str, int] = {}
        vector_ranks: dict[str, int] = {}
        for rank, (chunk, _) in enumerate(keyword, start=1):
            chunks[chunk.chunk_id] = chunk
            keyword_ranks[chunk.chunk_id] = rank
            scores[chunk.chunk_id] += 1.0 / (k + rank)
        for rank, (chunk, _) in enumerate(vector, start=1):
            chunks[chunk.chunk_id] = chunk
            vector_ranks[chunk.chunk_id] = rank
            scores[chunk.chunk_id] += 1.0 / (k + rank)
        return [
            SearchHit(chunks[key], score, keyword_ranks.get(key), vector_ranks.get(key))
            for key, score in sorted(scores.items(), key=lambda item: (-item[1], item[0]))
        ]

    @staticmethod
    def _rerank(query: str, hits: list[SearchHit], limit: int) -> list[SearchHit]:
        normalized_query = normalize_unicode(query).strip()
        query_terms = set(lexical_tokens(query))
        reranked: list[SearchHit] = []
        for hit in hits:
            content = normalize_unicode(hit.chunk.content)
            section = normalize_unicode(" ".join(hit.chunk.section_path))
            overlap = len(query_terms.intersection(lexical_tokens(content))) / max(
                1, len(query_terms)
            )
            phrase_bonus = 0.2 if normalized_query in content else 0.0
            section_bonus = 0.1 * len(query_terms.intersection(lexical_tokens(section))) / max(
                1, len(query_terms)
            )
            reranked.append(
                SearchHit(
                    chunk=hit.chunk,
                    score=hit.score + overlap + phrase_bonus + section_bonus,
                    keyword_rank=hit.keyword_rank,
                    vector_rank=hit.vector_rank,
                )
            )
        return sorted(reranked, key=lambda hit: (-hit.score, hit.chunk.chunk_id))[:limit]

    def _fingerprint(self, document: Document) -> str:
        payload = {
            "documentId": document.document_id,
            "title": document.title,
            "content": document.content,
            "sourceUri": document.source_uri,
            "dataSourceId": document.data_source_id,
            "evidenceId": document.evidence_id,
            "embeddingModel": self.embedder.model,
            "embeddingDimensions": self.embedder.dimensions,
            "normalizerVersion": self.normalizer.version,
        }
        encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
        return hashlib.sha256(encoded).hexdigest()

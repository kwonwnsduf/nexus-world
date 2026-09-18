from __future__ import annotations

import hashlib
from collections.abc import Iterator, Sequence
from contextlib import contextmanager
from typing import Any

import psycopg

from app.retrieval.models import Chunk


class PostgresRetrievalRepository:
    """PostgreSQL FTS and pgvector adapter; domain logic stays in RetrievalService."""

    def __init__(self, database_url: str) -> None:
        self.database_url = database_url

    @contextmanager
    def document_lock(self, document_id: str) -> Iterator[None]:
        first, second = self._advisory_lock_keys(document_id)
        connection = psycopg.connect(self.database_url, autocommit=True)
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT pg_advisory_lock(%s, %s)", (first, second))
            yield
        finally:
            try:
                with connection.cursor() as cursor:
                    cursor.execute("SELECT pg_advisory_unlock(%s, %s)", (first, second))
            finally:
                connection.close()

    def current_document(self, document_id: str, fingerprint: str) -> list[Chunk] | None:
        with psycopg.connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT content_sha256
                FROM rag_document_indexes
                WHERE document_id = %s
                """,
                (document_id,),
            )
            row = cursor.fetchone()
            if row is None or str(row[0]) != fingerprint:
                return None
            cursor.execute(
                self._chunk_select()
                + " WHERE rc.document_id = %s ORDER BY rc.ordinal",
                (document_id,),
            )
            return [self._chunk(chunk_row) for chunk_row in cursor.fetchall()]

    def replace_document(self, chunks: list[Chunk], fingerprint: str) -> None:
        if not chunks:
            return
        document_id = chunks[0].document_id
        with psycopg.connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute("DELETE FROM rag_chunks WHERE document_id = %s", (document_id,))
            cursor.executemany(
                """
                INSERT INTO rag_chunks (
                    id, document_id, title, source_uri, data_source_id, evidence_id,
                    section_path, content, search_text, ordinal, start_character,
                    end_character, embedding, embedding_model, embedding_status
                ) VALUES (
                    %s, %s, %s, %s,
                    COALESCE(%s::uuid, (SELECT source_id FROM evidence_items WHERE id = %s::uuid)),
                    %s::uuid, %s, %s, %s, %s, %s, %s, %s::vector, %s, 'READY'
                )
                """,
                [
                    (
                        chunk.chunk_id,
                        chunk.document_id,
                        chunk.title,
                        chunk.source_uri,
                        chunk.data_source_id,
                        chunk.evidence_id,
                        chunk.evidence_id,
                        list(chunk.section_path),
                        chunk.content,
                        chunk.search_text,
                        chunk.ordinal,
                        chunk.start_character,
                        chunk.end_character,
                        self._vector_literal(chunk.embedding),
                        chunk.embedding_model,
                    )
                    for chunk in chunks
                ],
            )
            first = chunks[0]
            cursor.execute(
                """
                INSERT INTO rag_document_indexes (
                    document_id, content_sha256, embedding_model, embedding_dimensions,
                    normalizer_version, indexed_at
                ) VALUES (%s, %s, %s, %s, 'kiwi-v1', CURRENT_TIMESTAMP)
                ON CONFLICT (document_id) DO UPDATE SET
                    content_sha256 = EXCLUDED.content_sha256,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dimensions = EXCLUDED.embedding_dimensions,
                    normalizer_version = EXCLUDED.normalizer_version,
                    indexed_at = EXCLUDED.indexed_at
                """,
                (
                    document_id,
                    fingerprint,
                    first.embedding_model,
                    len(first.embedding),
                ),
            )

    def keyword_search(self, query: str, limit: int) -> list[tuple[Chunk, float]]:
        if not query.strip():
            return []
        with psycopg.connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT rc.id, rc.document_id, rc.title,
                       COALESCE(ds.canonical_uri, rc.source_uri), rc.data_source_id,
                       rc.evidence_id, rc.section_path, rc.content, rc.search_text, rc.ordinal,
                       rc.start_character, rc.end_character, rc.embedding::text,
                       rc.embedding_model,
                       ts_rank_cd(
                           rc.search_vector,
                           to_tsquery('simple', regexp_replace(trim(%s), '\\s+', ' | ', 'g'))
                       ) AS score
                FROM rag_chunks rc
                LEFT JOIN evidence_items ei ON ei.id = rc.evidence_id
                LEFT JOIN data_sources ds ON ds.id = COALESCE(rc.data_source_id, ei.source_id)
                WHERE rc.search_vector @@ to_tsquery(
                    'simple', regexp_replace(trim(%s), '\\s+', ' | ', 'g')
                )
                ORDER BY score DESC, rc.id
                LIMIT %s
                """,
                (query, query, limit),
            )
            return [(self._chunk(row), float(row[14])) for row in cursor.fetchall()]

    def vector_search(
        self, embedding: tuple[float, ...], limit: int
    ) -> list[tuple[Chunk, float]]:
        literal = self._vector_literal(embedding)
        with psycopg.connect(self.database_url) as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT rc.id, rc.document_id, rc.title,
                       COALESCE(ds.canonical_uri, rc.source_uri), rc.data_source_id,
                       rc.evidence_id, rc.section_path, rc.content, rc.search_text, rc.ordinal,
                       rc.start_character, rc.end_character, rc.embedding::text,
                       rc.embedding_model,
                       1 - (rc.embedding <=> %s::vector) AS score
                FROM rag_chunks rc
                LEFT JOIN evidence_items ei ON ei.id = rc.evidence_id
                LEFT JOIN data_sources ds ON ds.id = COALESCE(rc.data_source_id, ei.source_id)
                WHERE rc.embedding_status = 'READY' AND rc.embedding IS NOT NULL
                ORDER BY rc.embedding <=> %s::vector, rc.id
                LIMIT %s
                """,
                (literal, literal, limit),
            )
            return [(self._chunk(row), float(row[14])) for row in cursor.fetchall()]

    @staticmethod
    def _vector_literal(embedding: tuple[float, ...]) -> str:
        return "[" + ",".join(f"{value:.9f}" for value in embedding) + "]"

    @staticmethod
    def _chunk(row: Sequence[Any]) -> Chunk:
        vector_text = str(row[12]) if row[12] is not None else ""
        vector = tuple(float(value) for value in vector_text.strip("[]").split(",") if value)
        return Chunk(
            chunk_id=str(row[0]),
            document_id=str(row[1]),
            title=str(row[2]),
            source_uri=str(row[3]) if row[3] is not None else None,
            data_source_id=str(row[4]) if row[4] is not None else None,
            evidence_id=str(row[5]) if row[5] is not None else None,
            section_path=tuple(str(value) for value in row[6]),
            content=str(row[7]),
            search_text=str(row[8]),
            ordinal=int(row[9]),
            start_character=int(row[10]),
            end_character=int(row[11]),
            embedding=vector,
            embedding_model=str(row[13]) if row[13] is not None else "",
        )

    @staticmethod
    def _advisory_lock_keys(document_id: str) -> tuple[int, int]:
        digest = hashlib.sha256(document_id.encode("utf-8")).digest()
        return (
            int.from_bytes(digest[:4], "big", signed=True),
            int.from_bytes(digest[4:8], "big", signed=True),
        )

    @staticmethod
    def _chunk_select() -> str:
        return """
            SELECT rc.id, rc.document_id, rc.title,
                   COALESCE(ds.canonical_uri, rc.source_uri), rc.data_source_id,
                   rc.evidence_id, rc.section_path, rc.content, rc.search_text, rc.ordinal,
                   rc.start_character, rc.end_character, rc.embedding::text,
                   rc.embedding_model
            FROM rag_chunks rc
            LEFT JOIN evidence_items ei ON ei.id = rc.evidence_id
            LEFT JOIN data_sources ds ON ds.id = COALESCE(rc.data_source_id, ei.source_id)
        """

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class SearchMode(StrEnum):
    KEYWORD = "keyword"
    VECTOR = "vector"
    HYBRID = "hybrid"


@dataclass(frozen=True)
class Document:
    document_id: str
    title: str
    content: str
    source_uri: str | None = None
    data_source_id: str | None = None
    evidence_id: str | None = None


@dataclass(frozen=True)
class Chunk:
    chunk_id: str
    document_id: str
    title: str
    source_uri: str | None
    data_source_id: str | None
    evidence_id: str | None
    section_path: tuple[str, ...]
    content: str
    search_text: str
    ordinal: int
    start_character: int
    end_character: int
    embedding: tuple[float, ...]
    embedding_model: str


@dataclass(frozen=True)
class SearchHit:
    chunk: Chunk
    score: float
    keyword_rank: int | None = None
    vector_rank: int | None = None

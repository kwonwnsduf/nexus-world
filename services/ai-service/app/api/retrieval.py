from __future__ import annotations

import os
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field

from app.retrieval.embedding import EmbeddingConfigurationError, EmbeddingServiceError
from app.retrieval.models import Chunk, Document, SearchHit, SearchMode
from app.retrieval.postgres import PostgresRetrievalRepository
from app.retrieval.repository import InMemoryRetrievalRepository, RetrievalRepository
from app.retrieval.service import RetrievalService

router = APIRouter(prefix="/api/v1/retrieval", tags=["retrieval"])


class IngestDocumentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    documentId: str = Field(min_length=1, max_length=160)
    title: str = Field(min_length=1, max_length=500)
    content: str = Field(min_length=1, max_length=2_000_000)
    sourceUri: str | None = Field(default=None, max_length=2_000)
    dataSourceId: UUID | None = None
    evidenceId: UUID | None = None


class ChunkResponse(BaseModel):
    chunkId: str
    documentId: str
    title: str
    sourceUri: str | None
    dataSourceId: UUID | None
    evidenceId: UUID | None
    sectionPath: tuple[str, ...]
    content: str
    ordinal: int
    startCharacter: int
    endCharacter: int


class IngestDocumentResponse(BaseModel):
    documentId: str
    chunkCount: int
    chunks: tuple[ChunkResponse, ...]


class SearchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    query: str = Field(min_length=1, max_length=2_000)
    mode: SearchMode = SearchMode.HYBRID
    limit: int = Field(default=5, ge=1, le=20)
    rerank: bool = True


class SearchHitResponse(BaseModel):
    chunk: ChunkResponse
    score: float
    keywordRank: int | None
    vectorRank: int | None


class SearchResponse(BaseModel):
    query: str
    mode: SearchMode
    reranked: bool
    hits: tuple[SearchHitResponse, ...]


class AnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    query: str = Field(min_length=1, max_length=2_000)
    limit: int = Field(default=5, ge=1, le=20)


class CitationResponse(BaseModel):
    index: int
    chunkId: str
    documentId: str
    title: str
    sourceUri: str | None
    dataSourceId: UUID | None
    evidenceId: UUID | None
    sectionPath: tuple[str, ...]
    startCharacter: int
    endCharacter: int


class AnswerResponse(BaseModel):
    answer: str
    citations: tuple[CitationResponse, ...]


def _repository() -> RetrievalRepository:
    database_url = os.getenv("RAG_DATABASE_URL")
    if database_url:
        return PostgresRetrievalRepository(database_url)
    return InMemoryRetrievalRepository()


service = RetrievalService(_repository())


def _chunk_response(chunk: Chunk) -> ChunkResponse:
    return ChunkResponse(
        chunkId=chunk.chunk_id,
        documentId=chunk.document_id,
        title=chunk.title,
        sourceUri=chunk.source_uri,
        dataSourceId=UUID(chunk.data_source_id) if chunk.data_source_id else None,
        evidenceId=UUID(chunk.evidence_id) if chunk.evidence_id else None,
        sectionPath=chunk.section_path,
        content=chunk.content,
        ordinal=chunk.ordinal,
        startCharacter=chunk.start_character,
        endCharacter=chunk.end_character,
    )


def _hit_response(hit: SearchHit) -> SearchHitResponse:
    return SearchHitResponse(
        chunk=_chunk_response(hit.chunk),
        score=hit.score,
        keywordRank=hit.keyword_rank,
        vectorRank=hit.vector_rank,
    )


@router.post("/documents", response_model=IngestDocumentResponse, status_code=201)
def ingest_document(request: IngestDocumentRequest) -> IngestDocumentResponse:
    try:
        chunks = service.ingest(
            Document(
                request.documentId,
                request.title,
                request.content,
                request.sourceUri,
                str(request.dataSourceId) if request.dataSourceId else None,
                str(request.evidenceId) if request.evidenceId else None,
            )
        )
    except EmbeddingConfigurationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except EmbeddingServiceError as error:
        raise HTTPException(status_code=502, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return IngestDocumentResponse(
        documentId=request.documentId,
        chunkCount=len(chunks),
        chunks=tuple(_chunk_response(chunk) for chunk in chunks),
    )


@router.post("/search", response_model=SearchResponse)
def search(request: SearchRequest) -> SearchResponse:
    try:
        hits = service.search(
            request.query,
            request.mode,
            request.limit,
            rerank=request.rerank,
        )
    except EmbeddingConfigurationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except EmbeddingServiceError as error:
        raise HTTPException(status_code=502, detail=str(error)) from error
    return SearchResponse(
        query=request.query,
        mode=request.mode,
        reranked=request.rerank,
        hits=tuple(_hit_response(hit) for hit in hits),
    )


@router.post("/answer", response_model=AnswerResponse)
def answer(request: AnswerRequest) -> AnswerResponse:
    try:
        answer_text, hits = service.answer(request.query, request.limit)
    except EmbeddingConfigurationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except EmbeddingServiceError as error:
        raise HTTPException(status_code=502, detail=str(error)) from error
    citations = tuple(
        CitationResponse(
            index=index,
            chunkId=hit.chunk.chunk_id,
            documentId=hit.chunk.document_id,
            title=hit.chunk.title,
            sourceUri=hit.chunk.source_uri,
            dataSourceId=UUID(hit.chunk.data_source_id) if hit.chunk.data_source_id else None,
            evidenceId=UUID(hit.chunk.evidence_id) if hit.chunk.evidence_id else None,
            sectionPath=hit.chunk.section_path,
            startCharacter=hit.chunk.start_character,
            endCharacter=hit.chunk.end_character,
        )
        for index, hit in enumerate(hits, start=1)
    )
    return AnswerResponse(answer=answer_text, citations=citations)


@router.get("/modes", response_model=tuple[SearchMode, ...])
def modes(
    include_experimental: Annotated[bool, Query(alias="includeExperimental")] = False,
) -> tuple[SearchMode, ...]:
    _ = include_experimental
    return tuple(SearchMode)

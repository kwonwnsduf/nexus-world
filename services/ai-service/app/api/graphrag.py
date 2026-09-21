from __future__ import annotations

import os
from typing import Annotated, Any
from uuid import UUID

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from app.api.retrieval import CitationResponse
from app.api.retrieval import service as retrieval_service
from app.graphrag.repository import CoreApiGraphRepository, GraphRepositoryError
from app.graphrag.service import GraphRagService

router = APIRouter(prefix="/api/v1/graphrag", tags=["graphrag"])


class GraphRagRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    worldVersionId: UUID
    query: str = Field(min_length=1, max_length=500)
    maxDepth: int = Field(default=3, ge=1, le=3)
    rootLimit: int = Field(default=5, ge=1, le=10)
    pathLimit: int = Field(default=10, ge=1, le=20)
    evidenceLimit: int = Field(default=5, ge=1, le=10)


class GraphNodeResponse(BaseModel):
    id: UUID
    entityType: str
    naturalKey: str
    displayName: str
    attributes: dict[str, Any]


class GraphRelationshipResponse(BaseModel):
    id: UUID
    relationshipType: str
    sourceEntityId: UUID
    targetEntityId: UUID
    attributes: dict[str, Any]


class RankedPathResponse(BaseModel):
    score: float
    nodes: tuple[GraphNodeResponse, ...]
    relationships: tuple[GraphRelationshipResponse, ...]


class GraphEvidenceResponse(BaseModel):
    evidenceId: UUID | None
    dataSourceId: UUID | None
    sourceUri: str | None
    ownerKind: str
    ownerId: UUID


class GraphRagResponse(BaseModel):
    contractVersion: str
    worldVersionId: UUID
    query: str
    answer: str
    roots: tuple[GraphNodeResponse, ...]
    paths: tuple[RankedPathResponse, ...]
    graphEvidence: tuple[GraphEvidenceResponse, ...]
    textCitations: tuple[CitationResponse, ...]


service = GraphRagService(
    CoreApiGraphRepository(os.getenv("CORE_API_URL", "http://core-api:8080")),
    retrieval_service,
)


def _node(node: Any) -> GraphNodeResponse:
    return GraphNodeResponse(
        id=UUID(node.id),
        entityType=node.entity_type,
        naturalKey=node.natural_key,
        displayName=node.display_name,
        attributes=node.attributes,
    )


@router.post("/query", response_model=GraphRagResponse)
def graph_query(
    request: GraphRagRequest,
    authorization: Annotated[str | None, Header()] = None,
) -> GraphRagResponse:
    if not authorization or not authorization.casefold().startswith("bearer "):
        raise HTTPException(status_code=401, detail="A bearer token is required")
    try:
        result = service.query(
            str(request.worldVersionId),
            request.query,
            max_depth=request.maxDepth,
            root_limit=request.rootLimit,
            path_limit=request.pathLimit,
            evidence_limit=request.evidenceLimit,
            authorization=authorization,
        )
    except GraphRepositoryError as error:
        raise HTTPException(status_code=error.status_code, detail=str(error)) from error

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
        for index, hit in enumerate(result.text_hits, start=1)
    )
    return GraphRagResponse(
        contractVersion="v1",
        worldVersionId=request.worldVersionId,
        query=request.query,
        answer=result.answer,
        roots=tuple(_node(node) for node in result.roots),
        paths=tuple(
            RankedPathResponse(
                score=ranked.score,
                nodes=tuple(_node(node) for node in ranked.path.nodes),
                relationships=tuple(
                    GraphRelationshipResponse(
                        id=UUID(edge.id),
                        relationshipType=edge.relationship_type,
                        sourceEntityId=UUID(edge.source_entity_id),
                        targetEntityId=UUID(edge.target_entity_id),
                        attributes=edge.attributes,
                    )
                    for edge in ranked.path.relationships
                ),
            )
            for ranked in result.paths
        ),
        graphEvidence=tuple(
            GraphEvidenceResponse(
                evidenceId=UUID(reference.evidence_id) if reference.evidence_id else None,
                dataSourceId=UUID(reference.data_source_id) if reference.data_source_id else None,
                sourceUri=reference.source_uri,
                ownerKind=reference.owner_kind,
                ownerId=UUID(reference.owner_id),
            )
            for reference in result.evidence
        ),
        textCitations=citations,
    )

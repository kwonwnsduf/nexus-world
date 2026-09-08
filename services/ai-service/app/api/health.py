from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict

router = APIRouter(tags=["health"])


class HealthResponse(BaseModel):
    model_config = ConfigDict(frozen=True)

    status: Literal["UP"] = "UP"
    service: Literal["ai-service"] = "ai-service"
    version: str = "dev"


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


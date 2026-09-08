from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict

router = APIRouter(prefix="/api/v1/platform", tags=["platform"])


class CapabilitiesResponse(BaseModel):
    model_config = ConfigDict(frozen=True)

    status: Literal["UP"] = "UP"
    service: Literal["ai-service"] = "ai-service"
    contractVersion: Literal["v1"] = "v1"
    capabilities: tuple[str, ...] = (
        "retrieval",
        "agent-orchestration",
        "population-synthesis",
        "deterministic-simulation",
    )


@router.get("/capabilities", response_model=CapabilitiesResponse)
def capabilities() -> CapabilitiesResponse:
    return CapabilitiesResponse()


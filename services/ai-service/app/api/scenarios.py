from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from app.scenarios.interpreter import ScenarioExtractionError, interpret

router = APIRouter(prefix="/api/v1/scenarios", tags=["scenarios"])


class InterpretScenarioRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    query: str = Field(min_length=3, max_length=500)


class InterpretScenarioResponse(BaseModel):
    contractVersion: str = "v2"
    originalQuery: str
    target: str
    metric: str
    changeType: str
    change: float
    duration: int | None
    optionalPolicy: str | None
    confidence: float


@router.post("/interpret", response_model=InterpretScenarioResponse)
def interpret_scenario(request: InterpretScenarioRequest) -> InterpretScenarioResponse:
    try:
        value = interpret(request.query)
    except ScenarioExtractionError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return InterpretScenarioResponse(
        originalQuery=value.original_query, target=value.target, metric=value.metric,
        changeType=value.change_type, change=value.change, duration=value.duration,
        optionalPolicy=value.optional_policy, confidence=value.confidence)

from fastapi import APIRouter

from app.simulation.engine import simulate
from app.simulation.models import SimulationRequest, SimulationResult

router = APIRouter(prefix="/api/v1/simulations", tags=["simulation"])


@router.post("/execute", response_model=SimulationResult)
def execute(request: SimulationRequest) -> SimulationResult:
    return simulate(request)

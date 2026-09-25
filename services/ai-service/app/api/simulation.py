from fastapi import APIRouter

from app.simulation.engine import simulate
from app.simulation.graph_engine import simulate_graph
from app.simulation.graph_models import GraphSimulationRequest
from app.simulation.models import SimulationRequest, SimulationResult

router = APIRouter(prefix="/api/v1/simulations", tags=["simulation"])


@router.post("/execute")
def execute(
    request: SimulationRequest | GraphSimulationRequest,
) -> SimulationResult | dict[str, object]:
    if isinstance(request, GraphSimulationRequest):
        return simulate_graph(request)
    return simulate(request)

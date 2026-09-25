from fastapi import FastAPI

from app.api.capabilities import router as capabilities_router
from app.api.graphrag import router as graphrag_router
from app.api.health import router as health_router
from app.api.retrieval import router as retrieval_router
from app.api.scenarios import router as scenarios_router
from app.api.simulation import router as simulation_router

app = FastAPI(
    title="NEXUS WORLD AI Service",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
)
app.include_router(health_router)
app.include_router(capabilities_router)
app.include_router(retrieval_router)
app.include_router(graphrag_router)
app.include_router(simulation_router)
app.include_router(scenarios_router)

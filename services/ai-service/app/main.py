from fastapi import FastAPI

from app.api.capabilities import router as capabilities_router
from app.api.health import router as health_router

app = FastAPI(
    title="NEXUS WORLD AI Service",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
)
app.include_router(health_router)
app.include_router(capabilities_router)

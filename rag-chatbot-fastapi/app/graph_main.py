from fastapi import FastAPI

from app.core.config import settings

app = FastAPI(title="Cacanode Graph Service", version="1.0.0-scaffold")


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "live"}


@app.get("/health/ready")
async def ready() -> dict[str, str]:
    return {"status": "ready", "database_path": settings.KUZU_DATABASE_PATH}

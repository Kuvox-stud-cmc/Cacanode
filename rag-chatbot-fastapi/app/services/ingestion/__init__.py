"""Document ingestion service module.

Exports the ingestion router for external use.
"""

from app.services.ingestion.router import router

__all__ = ["router"]

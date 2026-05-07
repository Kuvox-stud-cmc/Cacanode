"""LLM service module.

Exports the LLM gateway and router for external use.
"""

from app.services.llm.gateway import LLMGateway, get_llm, get_embeddings
from app.services.llm.router import router

__all__ = ["LLMGateway", "get_llm", "get_embeddings", "router"]

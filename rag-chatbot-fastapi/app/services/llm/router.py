"""LLM service router with completion and embedding endpoints.

Provides REST API endpoints for LLM text generation and embedding operations.
"""

from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status

from app.core.dependencies import get_current_tenant
from app.core.config import settings
from app.models.common import ApiResponse
from app.services.llm.gateway import LLMGateway

router = APIRouter()


class CompleteRequest:
    """Request model for LLM completion endpoint."""

    def __init__(
        self,
        prompt: str,
        system: Optional[str] = None,
        provider: Optional[str] = None,
        model: Optional[str] = None,
    ):
        self.prompt = prompt
        self.system = system
        self.provider = provider
        self.model = model


class EmbedRequest:
    """Request model for embedding endpoint."""

    def __init__(self, texts: list[str]):
        self.texts = texts


@router.post("/complete", response_model=ApiResponse[dict])
async def complete(
    request: dict,
    tenant: dict = Depends(get_current_tenant),
) -> ApiResponse[dict]:
    """Generate LLM completion for a given prompt.

    Uses the tenant's configured provider or falls back to system defaults.
    Supports optional provider/model override in the request.

    Args:
        request: JSON body with prompt, optional system message, provider, and model.
        tenant: Authenticated tenant context from JWT.

    Returns:
        ApiResponse containing the generated content and provider info.

    Raises:
        HTTPException: 400 if request is invalid, 500 if LLM call fails.
    """
    prompt = request.get("prompt")
    if not prompt:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Missing required field: prompt",
        )

    system = request.get("system")
    provider_override = request.get("provider")
    model_override = request.get("model")

    # Build tenant config from defaults and request overrides
    tenant_config = {
        "llm_provider": provider_override or "groq",
        "llm_model": model_override or settings.LLM_MODEL,
    }

    try:
        gateway = LLMGateway(tenant_config)
        content = await gateway.complete(prompt=prompt, system=system)

        return ApiResponse(
            success=True,
            data={
                "content": content,
                "provider": tenant_config["llm_provider"],
                "model": tenant_config["llm_model"],
            },
            message="Completion generated successfully",
        )
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e),
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"LLM completion failed: {str(e)}",
        )


@router.post("/embed", response_model=ApiResponse[dict])
async def embed(
    request: dict,
    tenant: dict = Depends(get_current_tenant),
) -> ApiResponse[dict]:
    """Generate embeddings for a list of texts.

    Uses the tenant's configured embedding provider or falls back to VoyageAI.

    Args:
        request: JSON body with list of texts to embed.
        tenant: Authenticated tenant context from JWT.

    Returns:
        ApiResponse containing the embedding vectors and model info.

    Raises:
        HTTPException: 400 if request is invalid, 500 if embedding fails.
    """
    texts = request.get("texts")
    if not texts or not isinstance(texts, list):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Missing or invalid field: texts (must be a list)",
        )

    # Build tenant config with default embedding provider
    tenant_config = {
        "embed_provider": "voyageai",
        "embed_model": settings.EMBED_MODEL,
    }

    try:
        gateway = LLMGateway(tenant_config)
        embeddings = await gateway.embed(texts=texts)

        return ApiResponse(
            success=True,
            data={
                "embeddings": embeddings,
                "count": len(embeddings),
                "dimensions": len(embeddings[0]) if embeddings else 0,
                "provider": tenant_config["embed_provider"],
                "model": tenant_config["embed_model"],
            },
            message=f"Generated {len(embeddings)} embeddings",
        )
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e),
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Embedding generation failed: {str(e)}",
        )

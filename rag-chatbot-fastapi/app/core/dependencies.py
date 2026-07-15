"""FastAPI dependencies for authentication and tenant extraction.

Provides JWT verification and tenant context injection for protected endpoints.
"""

import hashlib
import hmac
import time
from typing import Annotated, Any

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings

# HTTP Bearer security scheme for JWT tokens
security_bearer = HTTPBearer()
BearerCredentials = Annotated[HTTPAuthorizationCredentials, Depends(security_bearer)]
JWT_ALGORITHMS = ["HS256", "HS384", "HS512"]


async def get_current_tenant(
    credentials: BearerCredentials,
) -> dict[str, Any]:
    """Extract and validate tenant context from JWT token.

    Verifies the JWT signature using the shared secret, extracts tenant_id
    and user_id claims, and validates token expiration.

    Args:
        credentials: HTTP Authorization header with Bearer token.

    Returns:
        Dictionary containing tenant_id and user_id.

    Raises:
        HTTPException: 401 if token is invalid, expired, or missing required claims.
    """
    token = credentials.credentials

    try:
        # Decode and verify JWT using shared secret
        payload = jwt.decode(
            token,
            settings.TOKEN_KEY,
            algorithms=JWT_ALGORITHMS,
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired",
            headers={"WWW-Authenticate": "Bearer"},
        ) from None
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from None

    # Extract tenant_id from payload (camelCase as set by Spring Boot)
    tenant_id = payload.get("tenantId")
    if not tenant_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token missing tenant_id claim",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Extract user_id from payload (camelCase as set by Spring Boot)
    user_id = payload.get("userId")
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token missing user_id claim",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return {
        "tenant_id": str(tenant_id),
        "user_id": str(user_id),
        "email": payload.get("sub"),
        "role": payload.get("role"),
    }


async def get_integration_principal(
    request: Request,
    required_scope: str,
) -> dict[str, Any]:
    authorization = request.headers.get("Authorization", "")
    if not authorization.startswith("Bearer ccn_it_"):
        raise HTTPException(status_code=401, detail="Integration token is required")
    secret = authorization.removeprefix("Bearer ")
    token_hash = hmac.new(
        settings.INTEGRATION_TOKEN_PEPPER.encode(),
        secret.encode(),
        hashlib.sha256,
    ).hexdigest()
    try:
        import psycopg
        from psycopg.rows import dict_row

        with psycopg.connect(settings.POSTGRES_URL) as conn:
            with conn.cursor(row_factory=dict_row) as cur:
                cur.execute(
                    """
                    SELECT it.id, it.tenant_id, it.chatbot_id, c.knowledge_base_id,
                           it.scopes, it.expires_at, it.revoked_at, c.allowed_origins,
                           t.api_access_enabled
                    FROM integration_tokens it
                    JOIN chatbots c ON c.id = it.chatbot_id AND c.tenant_id = it.tenant_id
                    JOIN tenants t ON t.id = it.tenant_id
                    WHERE it.token_hash = %s AND c.status = 'ACTIVE'
                    """,
                    (token_hash,),
                )
                row = cur.fetchone()
                if row is not None:
                    cur.execute(
                        """
                        UPDATE integration_tokens
                        SET last_used_at = NOW(), updated_at = NOW()
                        WHERE id = %s
                        """,
                        (row["id"],),
                    )
            conn.commit()
    except Exception as exc:
        raise HTTPException(
            status_code=503, detail="Integration authentication unavailable"
        ) from exc
    if row is None or row["revoked_at"] is not None:
        raise HTTPException(status_code=401, detail="Integration token is invalid")
    if row["expires_at"] is not None and row["expires_at"].timestamp() <= time.time():
        raise HTTPException(status_code=401, detail="Integration token is expired")
    if required_scope not in row["scopes"]:
        raise HTTPException(status_code=403, detail="Integration token scope is insufficient")
    if required_scope == "api:chat" and not row["api_access_enabled"]:
        raise HTTPException(status_code=403, detail="API access requires Pro")
    if required_scope == "widget:chat":
        parent_origin = request.headers.get("X-Parent-Origin")
        allowed_origins = row["allowed_origins"] or []
        if allowed_origins and parent_origin not in allowed_origins:
            raise HTTPException(status_code=403, detail="Website origin is not allowed")
    await _enforce_integration_rate_limit(request, str(row["id"]))
    return {
        "token_id": str(row["id"]),
        "tenant_id": str(row["tenant_id"]),
        "chatbot_id": str(row["chatbot_id"]),
        "knowledge_base_id": str(row["knowledge_base_id"]),
        "scope": required_scope,
    }


async def get_widget_principal(request: Request) -> dict[str, Any]:
    return await get_integration_principal(request, "widget:chat")


async def get_api_principal(request: Request) -> dict[str, Any]:
    return await get_integration_principal(request, "api:chat")


async def _enforce_integration_rate_limit(request: Request, token_id: str) -> None:
    try:
        import redis.asyncio as redis

        client = redis.from_url(settings.REDIS_URL, decode_responses=True)
        minute = int(time.time() // 60)
        ip_address = request.client.host if request.client else "unknown"
        key = f"integration-rate:{token_id}:{ip_address}:{minute}"
        count = await client.incr(key)
        if count == 1:
            await client.expire(key, 120)
        await client.aclose()
        if count > settings.PUBLIC_RATE_LIMIT_PER_MINUTE:
            raise HTTPException(status_code=429, detail="Integration rate limit exceeded")
    except HTTPException:
        raise
    except Exception:
        # Authentication remains available if Redis is temporarily unavailable.
        return

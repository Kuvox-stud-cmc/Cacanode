"""FastAPI dependencies for authentication and tenant extraction.

Provides JWT verification and tenant context injection for protected endpoints.
"""

from typing import Annotated, Any

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings

# HTTP Bearer security scheme for JWT tokens
security_bearer = HTTPBearer()
BearerCredentials = Annotated[HTTPAuthorizationCredentials, Depends(security_bearer)]


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
            settings.JWT_ACCESS_SECRET,
            algorithms=[settings.JWT_ALGORITHM],
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

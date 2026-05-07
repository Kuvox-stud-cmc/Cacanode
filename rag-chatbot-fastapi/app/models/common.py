"""Common Pydantic models for API responses.

Provides generic response wrappers for consistent API output format.
"""

from datetime import datetime, timezone
from typing import Generic, TypeVar, Optional

from pydantic import BaseModel, ConfigDict, Field

T = TypeVar("T")


def utc_now() -> datetime:
    """Return current UTC datetime."""
    return datetime.now(timezone.utc)


class ApiResponse(BaseModel, Generic[T]):
    """Generic API response wrapper.

    Provides a consistent response format for all API endpoints,
    including success status, data payload, message, and timestamp.

    Type Parameters:
        T: The type of data contained in the response.
    """

    model_config = ConfigDict(from_attributes=True)

    success: bool = True
    data: Optional[T] = None
    message: str = ""
    timestamp: datetime = Field(default_factory=utc_now)


class ErrorResponse(BaseModel):
    """Error response model for failed operations.

    Used when an API request fails to provide structured error information.
    """

    model_config = ConfigDict(from_attributes=True)

    success: bool = False
    error: str = ""
    detail: str = ""


class PaginatedResponse(BaseModel, Generic[T]):
    """Generic paginated response wrapper.

    Provides pagination metadata along with a list of items.

    Type Parameters:
        T: The type of items in the paginated list.
    """

    model_config = ConfigDict(from_attributes=True)

    items: list[T] = []
    total: int = 0
    page: int = 1
    size: int = 20

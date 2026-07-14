class IngestionError(Exception):
    """Base ingestion pipeline error."""


class PermanentIngestionError(IngestionError):
    """Failure caused by invalid input that should not be retried."""


class TransientIngestionError(IngestionError):
    """Failure caused by infrastructure or temporary dependencies."""

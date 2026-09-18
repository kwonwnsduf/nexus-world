from __future__ import annotations

import os
import random
import time
from collections.abc import Callable, Sequence
from typing import Protocol

import httpx

DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small"
EMBEDDING_DIMENSIONS = 1_536


class EmbeddingError(RuntimeError):
    """Base class for explicit embedding failures."""


class EmbeddingConfigurationError(EmbeddingError):
    pass


class EmbeddingServiceError(EmbeddingError):
    pass


class EmbeddingProvider(Protocol):
    model: str
    dimensions: int

    def embed(self, value: str) -> tuple[float, ...]: ...

    def embed_many(self, values: Sequence[str]) -> list[tuple[float, ...]]: ...


class OpenAIEmbedding:
    """Batched OpenAI embedding client with bounded retry and no local fallback."""

    RETRYABLE_STATUS_CODES = frozenset({408, 409, 429, 500, 502, 503, 504})

    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
        *,
        dimensions: int = EMBEDDING_DIMENSIONS,
        batch_size: int = 128,
        max_attempts: int = 4,
        timeout_seconds: float | None = None,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
        jitter: Callable[[], float] = random.random,
    ) -> None:
        self.api_key = api_key if api_key is not None else os.getenv("OPENAI_API_KEY", "")
        self.model: str = model or os.getenv(
            "OPENAI_EMBEDDING_MODEL", DEFAULT_EMBEDDING_MODEL
        ) or DEFAULT_EMBEDDING_MODEL
        self.dimensions = dimensions
        self.batch_size = batch_size
        self.max_attempts = max_attempts
        self.timeout_seconds = timeout_seconds or float(
            os.getenv("OPENAI_EMBEDDING_TIMEOUT_SECONDS", "30")
        )
        self._client = client
        self._sleep = sleep
        self._jitter = jitter

    def embed(self, value: str) -> tuple[float, ...]:
        return self.embed_many([value])[0]

    def embed_many(self, values: Sequence[str]) -> list[tuple[float, ...]]:
        if not self.api_key:
            raise EmbeddingConfigurationError("OPENAI_API_KEY is not configured")
        if not values:
            return []
        if any(not value.strip() for value in values):
            raise ValueError("embedding input must not be blank")

        embeddings: list[tuple[float, ...]] = []
        for start in range(0, len(values), self.batch_size):
            embeddings.extend(self._request(list(values[start : start + self.batch_size])))
        return embeddings

    def _request(self, values: list[str]) -> list[tuple[float, ...]]:
        owns_client = self._client is None
        client = self._client or httpx.Client(timeout=self.timeout_seconds)
        try:
            for attempt in range(1, self.max_attempts + 1):
                try:
                    response = client.post(
                        "https://api.openai.com/v1/embeddings",
                        headers={"Authorization": f"Bearer {self.api_key}"},
                        json={
                            "input": values,
                            "model": self.model,
                            "dimensions": self.dimensions,
                            "encoding_format": "float",
                        },
                    )
                except (httpx.NetworkError, httpx.TimeoutException) as error:
                    if attempt == self.max_attempts:
                        raise EmbeddingServiceError(
                            "OpenAI embeddings request failed after retries"
                        ) from error
                    self._backoff(attempt, None)
                    continue

                if response.status_code in self.RETRYABLE_STATUS_CODES:
                    if attempt == self.max_attempts:
                        raise EmbeddingServiceError(
                            f"OpenAI embeddings request failed with status {response.status_code}"
                        )
                    self._backoff(attempt, response.headers.get("retry-after"))
                    continue
                if response.is_error:
                    raise EmbeddingServiceError(
                        f"OpenAI embeddings request failed with status {response.status_code}"
                    )

                try:
                    payload = response.json()
                    rows = sorted(payload.get("data", []), key=lambda row: int(row["index"]))
                    result = [tuple(float(value) for value in row["embedding"]) for row in rows]
                except (KeyError, TypeError, ValueError) as error:
                    raise EmbeddingServiceError(
                        "OpenAI embeddings response was malformed"
                    ) from error
                if len(result) != len(values):
                    raise EmbeddingServiceError("OpenAI embeddings response count mismatch")
                if any(len(vector) != self.dimensions for vector in result):
                    raise EmbeddingServiceError("OpenAI embeddings response dimension mismatch")
                return result
            raise EmbeddingServiceError("OpenAI embeddings request exhausted retries")
        finally:
            if owns_client:
                client.close()

    def _backoff(self, attempt: int, retry_after: str | None) -> None:
        if retry_after:
            try:
                delay = min(float(retry_after), 30.0)
            except ValueError:
                delay = 0.0
        else:
            delay = 0.0
        if delay <= 0:
            delay = min(0.5 * (2 ** (attempt - 1)) + self._jitter() * 0.25, 8.0)
        self._sleep(delay)

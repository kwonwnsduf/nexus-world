from __future__ import annotations

import json

import httpx
import pytest

from app.retrieval.embedding import (
    EmbeddingConfigurationError,
    EmbeddingServiceError,
    OpenAIEmbedding,
)


def test_openai_embedding_batches_and_requests_explicit_dimensions() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        payload = json.loads(request.content)
        data = [
            {"index": index, "embedding": [float(index + 1), 0.0, 0.0]}
            for index, _ in enumerate(payload["input"])
        ]
        assert payload["model"] == "text-embedding-3-small"
        assert payload["dimensions"] == 3
        return httpx.Response(200, json={"data": data, "model": payload["model"]})

    client = httpx.Client(transport=httpx.MockTransport(handler))
    embedding = OpenAIEmbedding(
        api_key="test-only-not-a-real-key",
        dimensions=3,
        batch_size=2,
        client=client,
    )

    result = embedding.embed_many(["one", "two", "three"])

    assert len(requests) == 2
    assert result == [(1.0, 0.0, 0.0), (2.0, 0.0, 0.0), (1.0, 0.0, 0.0)]


def test_openai_embedding_retries_rate_limit_then_succeeds() -> None:
    attempts = 0
    sleeps: list[float] = []

    def handler(_: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            return httpx.Response(429, headers={"retry-after": "0.01"})
        return httpx.Response(200, json={"data": [{"index": 0, "embedding": [1.0]}]})

    embedding = OpenAIEmbedding(
        api_key="test-only-not-a-real-key",
        dimensions=1,
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        sleep=sleeps.append,
    )

    assert embedding.embed("retry") == (1.0,)
    assert attempts == 2
    assert sleeps == [0.01]


def test_openai_embedding_never_falls_back_when_key_or_service_is_unavailable() -> None:
    with pytest.raises(EmbeddingConfigurationError):
        OpenAIEmbedding(api_key="").embed("no key")

    client = httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(503)))
    embedding = OpenAIEmbedding(
        api_key="test-only-not-a-real-key",
        dimensions=1,
        max_attempts=1,
        client=client,
    )
    with pytest.raises(EmbeddingServiceError):
        embedding.embed("service unavailable")


def test_openai_embedding_surfaces_malformed_success_response() -> None:
    client = httpx.Client(
        transport=httpx.MockTransport(lambda _: httpx.Response(200, json={"data": [{}]}))
    )
    embedding = OpenAIEmbedding(api_key="test-only-not-a-real-key", client=client)

    with pytest.raises(EmbeddingServiceError, match="malformed"):
        embedding.embed("test")

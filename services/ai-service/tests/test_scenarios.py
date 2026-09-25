import json

import httpx
import pytest

from app.scenarios.interpreter import ScenarioExtractionError, interpret


def _client(payload: dict[str, object]) -> httpx.Client:
    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        assert body["response_format"]["type"] == "json_schema"
        return httpx.Response(
            200, json={"choices": [{"message": {"content": json.dumps(payload)}}]}
        )

    return httpx.Client(transport=httpx.MockTransport(handler))


def test_openai_extracts_candidate_without_parameters(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    client = _client({
        "target": "NVIDIA", "metric": "PRODUCTION", "changeType": "PERCENT",
        "change": -0.5, "duration": None, "optionalPolicy": None, "confidence": 0.96,
    })
    try:
        value = interpret("NVIDIA 생산이 50% 감소하면?", client=client)
    finally:
        client.close()
    assert value.target == "NVIDIA"
    assert value.metric == "PRODUCTION"
    assert value.change == -0.5
    assert not hasattr(value, "company_id")


def test_missing_openai_key_fails_closed(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    with pytest.raises(ScenarioExtractionError, match="OPENAI_API_KEY"):
        interpret("대만 반도체 공급이 50% 감소하면?")

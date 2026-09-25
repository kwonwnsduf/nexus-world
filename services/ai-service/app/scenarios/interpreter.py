from __future__ import annotations

import json
import os
from dataclasses import dataclass

import httpx


class ScenarioExtractionError(ValueError):
    """Raised when a query cannot be converted to the bounded scenario contract."""


@dataclass(frozen=True)
class StructuredScenario:
    original_query: str
    target: str
    metric: str
    change_type: str
    change: float
    duration: int | None
    optional_policy: str | None
    confidence: float


_SCHEMA = {
    "name": "nexus_world_scenario_candidate",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": [
            "target", "metric", "changeType", "change", "duration",
            "optionalPolicy", "confidence",
        ],
        "properties": {
            "target": {"type": "string", "minLength": 1, "maxLength": 160},
            "metric": {
                "type": "string",
                "enum": ["SUPPLY", "PRODUCTION", "CAPACITY", "DEMAND", "LOGISTICS"],
            },
            "changeType": {"type": "string", "enum": ["PERCENT"]},
            "change": {"type": "number", "minimum": -1.0, "maximum": 1.0},
            "duration": {"type": ["integer", "null"], "minimum": 1, "maximum": 120},
            "optionalPolicy": {"type": ["string", "null"], "maxLength": 240},
            "confidence": {"type": "number", "minimum": 0.0, "maximum": 1.0},
        },
    },
}


def interpret(query: str, *, client: httpx.Client | None = None) -> StructuredScenario:
    original = query.strip()
    if not original:
        raise ScenarioExtractionError("A scenario description is required")
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key:
        raise ScenarioExtractionError(
            "OPENAI_API_KEY is required for structured scenario extraction"
        )
    owns_client = client is None
    timeout = float(os.getenv("OPENAI_SCENARIO_TIMEOUT_SECONDS", "30"))
    transport = client or httpx.Client(timeout=timeout)
    try:
        response = transport.post(
            os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
            + "/chat/completions",
            headers={"Authorization": f"Bearer {api_key}"},
            json={
                "model": os.getenv("OPENAI_SCENARIO_MODEL", "gpt-4.1-mini"),
                "temperature": 0,
                "messages": [
                    {"role": "system", "content": (
                        "Extract only the user's requested what-if shock. "
                        "For entity matching, express the target as its canonical English name "
                        "followed by the user's original-language name in parentheses "
                        "when they differ. "
                        "Use a negative decimal for a decrease "
                        "and a positive decimal for an increase. Do not resolve entities, "
                        "invent baseline values, derive propagation coefficients, "
                        "or calculate simulation results.")},
                    {"role": "user", "content": original},
                ],
                "response_format": {"type": "json_schema", "json_schema": _SCHEMA},
            },
        )
        response.raise_for_status()
        value = json.loads(response.json()["choices"][0]["message"]["content"])
    except (httpx.HTTPError, KeyError, IndexError, TypeError, json.JSONDecodeError) as error:
        raise ScenarioExtractionError("OpenAI structured scenario extraction failed") from error
    finally:
        if owns_client:
            transport.close()
    change = float(value["change"])
    if change == 0:
        raise ScenarioExtractionError("The scenario must contain a non-zero change")
    return StructuredScenario(original, str(value["target"]).strip(), str(value["metric"]),
                              str(value["changeType"]), change, value["duration"],
                              value["optionalPolicy"], float(value["confidence"]))

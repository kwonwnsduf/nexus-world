from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any

import httpx


def path_metrics(
    expected: set[tuple[str, ...]], predicted: list[tuple[str, ...]]
) -> dict[str, float]:
    predicted_set = set(predicted)
    true_positive = len(expected.intersection(predicted_set))
    precision = true_positive / len(predicted_set) if predicted_set else 0.0
    recall = true_positive / len(expected) if expected else 1.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {"precision": precision, "recall": recall, "f1": f1}


def evaluate(
    dataset: dict[str, Any], predictions: dict[str, list[tuple[str, ...]]]
) -> dict[str, Any]:
    cases: list[dict[str, Any]] = dataset["cases"]
    results: list[dict[str, Any]] = []
    for case in cases:
        expected = {tuple(path) for path in case["expectedRelationshipPaths"]}
        predicted = predictions.get(str(case["id"]), [])
        results.append({"id": case["id"], **path_metrics(expected, predicted)})
    return {
        "datasetVersion": dataset["version"],
        "caseCount": len(results),
        "macroPathF1": sum(row["f1"] for row in results) / len(results) if results else 0.0,
        "cases": results,
    }


def fetch_predictions(
    dataset: dict[str, Any], base_url: str, world_version_id: str, token: str
) -> dict[str, list[tuple[str, ...]]]:
    predictions: dict[str, list[tuple[str, ...]]] = {}
    with httpx.Client(base_url=base_url, timeout=10.0) as client:
        for case in dataset["cases"]:
            response = client.post(
                "/api/v1/graphrag/query",
                headers={"Authorization": f"Bearer {token}"},
                json={
                    "worldVersionId": world_version_id,
                    "query": case["query"],
                    "maxDepth": 3,
                    "pathLimit": 20,
                },
            )
            response.raise_for_status()
            payload: dict[str, Any] = response.json()
            predictions[str(case["id"])] = [
                tuple(str(edge["relationshipType"]) for edge in path["relationships"])
                for path in payload["paths"]
            ]
    return predictions


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate frozen GraphRAG path judgments")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--world-version-id", required=True)
    parser.add_argument("--token", default=os.getenv("NEXUS_EVAL_TOKEN"))
    arguments = parser.parse_args()
    if not arguments.token:
        parser.error("--token or NEXUS_EVAL_TOKEN is required")
    dataset = json.loads(arguments.dataset.read_text(encoding="utf-8"))
    predictions = fetch_predictions(
        dataset, arguments.base_url, arguments.world_version_id, arguments.token
    )
    print(json.dumps(evaluate(dataset, predictions), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

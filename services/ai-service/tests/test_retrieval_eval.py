from app.evals.retrieval import EvaluationQuery, metrics, percentile


def test_retrieval_metrics_are_reproducible() -> None:
    query_one = EvaluationQuery("q1", "first", frozenset({"doc-a"}))
    query_two = EvaluationQuery("q2", "second", frozenset({"doc-b"}))

    result = metrics(
        [
            (query_one, ["doc-a", "doc-c"], 10.0),
            (query_two, ["doc-c", "doc-b"], 30.0),
        ],
        k=2,
    )

    assert result["recall@2"] == 1.0
    assert result["mrr"] == 0.75
    assert result["latencyP50Ms"] == 10.0
    assert result["latencyP95Ms"] == 30.0


def test_percentile_handles_empty_results() -> None:
    assert percentile([], 0.95) == 0.0

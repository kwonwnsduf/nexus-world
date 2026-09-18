# Retrieval evaluation

The versioned fixture `services/ai-service/app/evals/datasets/retrieval-v1.json` contains six Korean/English documents,
queries, and relevance judgments. Run the four-strategy comparison against a healthy Compose stack:

```powershell
docker compose exec -T ai-service python -m app.evals.retrieval `
  --dataset app/evals/datasets/retrieval-v1.json `
  --base-url http://127.0.0.1:8000 --k 5 --repeats 3
```

The command indexes the fixture idempotently, warms each strategy once, and emits JSON containing Recall@K, MRR, and
HTTP p50/p95 latency. It also evaluates the frozen Day 17 `simple-v1`/384-dimensional feature-hashing implementation in
process. Its latency is deliberately labelled non-comparable; quality deltas are comparable on the same judgments.

The checked result in `docs/evaluation/retrieval-v1-openai.json` was measured on 2026-09-17 against PostgreSQL 16 with
pgvector, Kiwi `0.23.2`, and OpenAI `text-embedding-3-small` at 1536 dimensions. This small fixture is a regression gate,
not a production relevance benchmark.

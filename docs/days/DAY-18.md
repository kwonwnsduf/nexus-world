# Day 18: Evidence-grounded GraphRAG

Status: Complete

Day 18 connects PostgreSQL-indexed entity retrieval, the Day 14 bounded Neo4j projection, and the Day 17 evidence index.
`POST /api/v1/graphrag/query` matches query text against deterministic entity search documents, traverses at most three hops, ranks and
deduplicates industrial-to-society paths, and returns both graph provenance and indexed text citations. It never mutates
the canonical PostgreSQL graph.

## Alias and traversal safety

PostgreSQL stores normalized aliases from display name, natural key, and explicit `aliases`, `alias`, `symbol`, `code`,
or `name` attributes. A database trigger keeps alias rows and an FTS document synchronized with every entity write.
Candidate generation combines indexed exact matching, `pg_trgm` strict-word similarity, and GIN-backed FTS; only the
highest-scoring entity IDs reach Neo4j. Alias matching selects retrieval roots only and never merges identities. Queries
are world-version scoped, root and path counts are bounded, traversal remains capped at three hops, and stable IDs
provide deterministic tie-breaking.

## Evidence grounding

GraphRAG recursively extracts `evidenceId`, `dataSourceId`, and `sourceUri` references from returned node and relationship
attributes. The highest-ranked paths also expand a keyword-only Day 17 evidence query, avoiding a mandatory embedding or
external model call. Responses distinguish graph evidence references from text citations and return a safe empty answer
when no alias matches.

## Evaluation and operation

`app/evals/graphrag.py` reports exact relationship-path precision, recall, and F1 against the versioned
`app/evals/datasets/graphrag-v1.json` fixture. Unit coverage verifies Korean/English aliases, the three-hop
Facility–Company–Cohort–Household path, provenance extraction, safe no-match behavior, and path F1 calculation.

Run the evaluation against a projected fixture world with an analyst token:

```powershell
$env:NEXUS_EVAL_TOKEN = "<token>"
docker compose exec -T ai-service python -m app.evals.graphrag `
  --dataset app/evals/datasets/graphrag-v1.json `
  --base-url http://127.0.0.1:8000 --world-version-id <world-version-uuid>
```

Neo4j remains optional for health checks. Enable `NEO4J_ENABLED=true`, project the desired world version, then call the
GraphRAG endpoint. A disabled or unavailable graph returns `503` rather than silently falling back to invented paths.
The caller's Bearer token is forwarded to Core API, so the existing JWT boundary remains authoritative; missing,
invalid, or insufficient credentials remain `401`/`403` responses.

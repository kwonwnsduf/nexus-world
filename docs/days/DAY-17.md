# Day 17: Hybrid retrieval, RRF, reranking, and citations

Status: Complete

Hybrid search retrieves independent FTS and vector candidate lists, combines them with reciprocal-rank fusion (`k=60`),
then applies a bounded deterministic reranker using query coverage, exact phrase, and section-title signals. Stable chunk
ID tie-breaking makes results reproducible.

`POST /api/v1/retrieval/answer` returns extractive statements with numbered citations. Each citation includes the source
document, section path, chunk ID, character range, and nullable Day 6 `evidenceId`/`dataSourceId`. When provenance is
present, the canonical URI from `data_sources` takes precedence while the original source URI remains supported for
legacy chunks. The database enforces chunk-to-evidence/source foreign keys and evidence/source consistency.

The reproducible evaluation harness in `app/evals/retrieval.py` compares FTS, vector, hybrid RRF, and hybrid with
reranking, plus the frozen simple-FTS/384-dimension feature-hashing baseline. It reports Recall@K, MRR, and warmed HTTP
p50/p95 latency; see `docs/RETRIEVAL_EVALUATION.md`.

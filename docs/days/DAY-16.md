# Day 16: PostgreSQL FTS and pgvector baseline

Status: Complete

Flyway migration V9 introduced the 384-dimensional baseline. V11 preserves those values in nullable
`legacy_embedding`, adds a nullable 1536-dimensional semantic `embedding`, rebuilds the partial HNSW cosine index, and
marks old rows `PENDING_REINDEX`. V12 makes the preserved legacy column nullable. Re-ingesting a document creates the
new embedding atomically and marks its chunks `READY`; vector search never reads pending legacy rows.

Production embedding uses OpenAI `text-embedding-3-small`, explicitly requests 1536 dimensions, batches chunk inputs,
and retries bounded transient failures with exponential backoff. Missing credentials and upstream failures are returned
explicitly; there is no feature-hashing fallback.

Index and query text are normalized through the same Kiwi pipeline. The original content remains untouched while nouns,
verbs/adjectives (lemma form), English, numbers, and CJK tokens are stored separately in `search_text`; PostgreSQL GIN
FTS remains independent of vector availability.

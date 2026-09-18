# Day 15: Section-aware text parsing and chunking

Status: Complete

The AI service accepts UTF-8 text through `POST /api/v1/retrieval/documents`. Markdown headings establish a stable
section path, chunks prefer whitespace boundaries, and bounded overlap preserves context. Every chunk records document,
section, ordinal, and exact character offsets. Chunk IDs are deterministic, so replaying the same document produces
identical locators. Indexing now acquires a PostgreSQL advisory lock scoped to the document ID and checks a content,
model, dimension, normalizer, and provenance fingerprint before calling the embedding API. Repeated or concurrent
requests therefore return the existing chunks without duplicate embeddings.

Parser tests cover nested sections, repeatability, and locator integrity.

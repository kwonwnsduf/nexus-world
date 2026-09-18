CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE rag_chunks (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(160) NOT NULL,
    title VARCHAR(500) NOT NULL,
    section_path TEXT[] NOT NULL DEFAULT '{}',
    content TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    start_character INTEGER NOT NULL,
    end_character INTEGER NOT NULL,
    embedding vector(384) NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', title), 'A') ||
        setweight(to_tsvector('simple', content), 'B')
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rag_chunks_content_not_blank CHECK (length(trim(content)) > 0),
    CONSTRAINT rag_chunks_ordinal_nonnegative CHECK (ordinal >= 0),
    CONSTRAINT rag_chunks_locator_valid CHECK (
        start_character >= 0 AND end_character > start_character
    ),
    CONSTRAINT rag_chunks_document_ordinal_unique UNIQUE (document_id, ordinal)
);

CREATE INDEX rag_chunks_document_idx ON rag_chunks(document_id, ordinal);
CREATE INDEX rag_chunks_search_idx ON rag_chunks USING GIN(search_vector);
CREATE INDEX rag_chunks_embedding_idx ON rag_chunks USING hnsw (embedding vector_cosine_ops);

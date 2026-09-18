DROP INDEX IF EXISTS rag_chunks_embedding_idx;
DROP INDEX IF EXISTS rag_chunks_search_idx;

ALTER TABLE rag_chunks RENAME COLUMN embedding TO legacy_embedding;
ALTER TABLE rag_chunks DROP COLUMN search_vector;
ALTER TABLE rag_chunks
    ADD COLUMN search_text TEXT NOT NULL DEFAULT '',
    ADD COLUMN embedding vector(1536),
    ADD COLUMN embedding_model VARCHAR(120),
    ADD COLUMN embedding_status VARCHAR(16) NOT NULL DEFAULT 'PENDING_REINDEX',
    ADD COLUMN data_source_id UUID REFERENCES data_sources(id) ON DELETE RESTRICT,
    ADD COLUMN evidence_id UUID REFERENCES evidence_items(id) ON DELETE RESTRICT;

UPDATE rag_chunks
SET search_text = lower(concat_ws(' ', title, array_to_string(section_path, ' '), content));

ALTER TABLE rag_chunks
    ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', search_text)
    ) STORED,
    ADD CONSTRAINT rag_chunks_embedding_status_valid CHECK (
        embedding_status IN ('PENDING_REINDEX', 'READY')
    ),
    ADD CONSTRAINT rag_chunks_ready_embedding_valid CHECK (
        (embedding_status = 'READY' AND embedding IS NOT NULL AND embedding_model IS NOT NULL)
        OR (embedding_status = 'PENDING_REINDEX' AND embedding IS NULL)
    );

CREATE TABLE rag_document_indexes (
    document_id VARCHAR(160) PRIMARY KEY,
    content_sha256 VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(120) NOT NULL,
    embedding_dimensions INTEGER NOT NULL,
    normalizer_version VARCHAR(80) NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT rag_document_indexes_sha256_valid CHECK (
        content_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT rag_document_indexes_dimensions_valid CHECK (embedding_dimensions = 1536)
);

CREATE OR REPLACE FUNCTION validate_rag_chunk_provenance()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.evidence_id IS NOT NULL AND NEW.data_source_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM evidence_items
        WHERE id = NEW.evidence_id AND source_id = NEW.data_source_id
    ) THEN
        RAISE EXCEPTION 'rag chunk evidence and data source do not match';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER rag_chunks_provenance_consistency
BEFORE INSERT OR UPDATE OF evidence_id, data_source_id ON rag_chunks
FOR EACH ROW EXECUTE FUNCTION validate_rag_chunk_provenance();

CREATE INDEX rag_chunks_search_idx ON rag_chunks USING GIN(search_vector);
CREATE INDEX rag_chunks_embedding_idx
    ON rag_chunks USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
CREATE INDEX rag_chunks_data_source_idx
    ON rag_chunks(data_source_id) WHERE data_source_id IS NOT NULL;
CREATE INDEX rag_chunks_evidence_idx
    ON rag_chunks(evidence_id) WHERE evidence_id IS NOT NULL;

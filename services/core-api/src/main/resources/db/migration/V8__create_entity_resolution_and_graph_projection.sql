CREATE TABLE entity_resolution_keys (
    id UUID PRIMARY KEY,
    world_version_id UUID NOT NULL REFERENCES world_versions(id) ON DELETE RESTRICT,
    entity_id UUID NOT NULL,
    entity_type VARCHAR(48) NOT NULL,
    key_scheme VARCHAR(48) NOT NULL,
    normalized_value VARCHAR(512) NOT NULL,
    source_system VARCHAR(32),
    confidence NUMERIC(5,4) NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT entity_resolution_entity_fk FOREIGN KEY (world_version_id, entity_id, entity_type)
        REFERENCES world_graph_entities(world_version_id, id, entity_type) ON DELETE RESTRICT,
    CONSTRAINT entity_resolution_scheme_format CHECK (key_scheme ~ '^[A-Z][A-Z0-9_]{1,47}$'),
    CONSTRAINT entity_resolution_value_not_blank CHECK (length(trim(normalized_value)) > 0),
    CONSTRAINT entity_resolution_confidence_range CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT entity_resolution_key_unique UNIQUE
        (world_version_id, entity_type, key_scheme, normalized_value),
    CONSTRAINT entity_resolution_entity_scheme_unique UNIQUE
        (world_version_id, entity_id, key_scheme, normalized_value)
);

CREATE INDEX entity_resolution_entity_idx
    ON entity_resolution_keys(world_version_id, entity_id);

CREATE TABLE graph_projection_runs (
    id UUID PRIMARY KEY,
    world_version_id UUID NOT NULL REFERENCES world_versions(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    entity_count INTEGER NOT NULL DEFAULT 0,
    relationship_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    initiated_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT graph_projection_status_valid CHECK (status IN ('RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT graph_projection_counts_nonnegative CHECK
        (entity_count >= 0 AND relationship_count >= 0)
);

CREATE INDEX graph_projection_world_started_idx
    ON graph_projection_runs(world_version_id, started_at DESC);

CREATE TABLE entity_name_aliases (
    id UUID PRIMARY KEY,
    world_version_id UUID NOT NULL REFERENCES world_versions(id) ON DELETE RESTRICT,
    entity_id UUID NOT NULL,
    entity_type VARCHAR(48) NOT NULL,
    alias_value VARCHAR(512) NOT NULL,
    normalized_value VARCHAR(512) NOT NULL,
    locale VARCHAR(16),
    source_system VARCHAR(32),
    confidence NUMERIC(5,4) NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT entity_name_alias_entity_fk FOREIGN KEY (world_version_id, entity_id, entity_type)
        REFERENCES world_graph_entities(world_version_id, id, entity_type) ON DELETE RESTRICT,
    CONSTRAINT entity_name_alias_value_not_blank CHECK (length(trim(alias_value)) > 0),
    CONSTRAINT entity_name_alias_normalized_not_blank CHECK (length(trim(normalized_value)) > 0),
    CONSTRAINT entity_name_alias_confidence_range CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT entity_name_alias_unique UNIQUE (world_version_id, entity_id, normalized_value)
);

CREATE INDEX entity_name_alias_lookup_idx
    ON entity_name_aliases(world_version_id, entity_type, normalized_value);

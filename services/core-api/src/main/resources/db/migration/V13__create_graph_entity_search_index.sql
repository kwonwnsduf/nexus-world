CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE graph_entity_search_documents (
    entity_id UUID PRIMARY KEY REFERENCES world_graph_entities(id) ON DELETE CASCADE,
    world_version_id UUID NOT NULL,
    entity_type VARCHAR(48) NOT NULL,
    search_text TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple', search_text)
    ) STORED,
    CONSTRAINT graph_entity_search_entity_fk
        FOREIGN KEY (world_version_id, entity_id, entity_type)
        REFERENCES world_graph_entities(world_version_id, id, entity_type) ON DELETE CASCADE
);

CREATE TABLE graph_entity_aliases (
    world_version_id UUID NOT NULL,
    entity_id UUID NOT NULL REFERENCES world_graph_entities(id) ON DELETE CASCADE,
    normalized_alias TEXT NOT NULL,
    alias_type VARCHAR(24) NOT NULL,
    PRIMARY KEY (world_version_id, entity_id, normalized_alias),
    CONSTRAINT graph_entity_alias_not_blank CHECK (length(trim(normalized_alias)) >= 2)
);

CREATE INDEX graph_entity_search_world_idx
    ON graph_entity_search_documents(world_version_id, entity_type);
CREATE INDEX graph_entity_search_fts_idx
    ON graph_entity_search_documents USING GIN(search_vector);
CREATE INDEX graph_entity_alias_exact_idx
    ON graph_entity_aliases(world_version_id, normalized_alias);
CREATE INDEX graph_entity_alias_trgm_idx
    ON graph_entity_aliases USING GIST(normalized_alias gist_trgm_ops);

CREATE OR REPLACE FUNCTION normalize_graph_alias(value TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT lower(regexp_replace(trim(normalize(COALESCE(value, ''), NFKC)), '\s+', ' ', 'g'));
$$;

CREATE OR REPLACE FUNCTION refresh_graph_entity_search()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    aliases TEXT;
    descriptors TEXT;
BEGIN
    DELETE FROM graph_entity_aliases WHERE entity_id = NEW.id;
    DELETE FROM graph_entity_search_documents WHERE entity_id = NEW.id;

    INSERT INTO graph_entity_aliases(world_version_id, entity_id, normalized_alias, alias_type)
    SELECT NEW.world_version_id, NEW.id, value, alias_type
    FROM (
        SELECT normalize_graph_alias(NEW.natural_key), 'NATURAL_KEY'
        UNION ALL
        SELECT normalize_graph_alias(NEW.display_name), 'DISPLAY_NAME'
        UNION ALL
        SELECT normalize_graph_alias(item), 'ALIAS'
        FROM jsonb_array_elements_text(
            CASE WHEN jsonb_typeof(NEW.attributes -> 'aliases') = 'array'
                THEN NEW.attributes -> 'aliases' ELSE '[]'::jsonb END
        ) AS item
        UNION ALL
        SELECT normalize_graph_alias(NEW.attributes ->> field_name), upper(field_name)
        FROM unnest(ARRAY['alias','symbol','code','name']) AS field_name
        WHERE jsonb_typeof(NEW.attributes -> field_name) = 'string'
    ) candidates(value, alias_type)
    WHERE length(value) >= 2
    ON CONFLICT (world_version_id, entity_id, normalized_alias) DO NOTHING;

    SELECT string_agg(normalized_alias, ' ' ORDER BY normalized_alias)
    INTO aliases
    FROM graph_entity_aliases
    WHERE entity_id = NEW.id;

    SELECT string_agg(NEW.attributes ->> field_name, ' ' ORDER BY field_name)
    INTO descriptors
    FROM unnest(ARRAY[
        'description','keywords','regionCode','countryCode','industryCode',
        'classificationCode','facilityKind','jurisdiction'
    ]) AS field_name
    WHERE jsonb_typeof(NEW.attributes -> field_name) = 'string';

    INSERT INTO graph_entity_search_documents(
        entity_id, world_version_id, entity_type, search_text
    ) VALUES (
        NEW.id, NEW.world_version_id, NEW.entity_type,
        normalize_graph_alias(concat_ws(' ', aliases, descriptors))
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER world_graph_entity_search_refresh
AFTER INSERT OR UPDATE OF world_version_id, entity_type, natural_key, display_name, attributes
ON world_graph_entities
FOR EACH ROW EXECUTE FUNCTION refresh_graph_entity_search();

UPDATE world_graph_entities SET display_name = display_name;

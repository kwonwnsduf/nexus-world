CREATE TABLE data_sources (
    id UUID PRIMARY KEY,
    source_key VARCHAR(160) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    title VARCHAR(500) NOT NULL,
    publisher VARCHAR(240),
    canonical_uri TEXT,
    source_version VARCHAR(120),
    license VARCHAR(160),
    published_at TIMESTAMPTZ,
    retrieved_at TIMESTAMPTZ NOT NULL,
    content_sha256 VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT data_sources_key_unique UNIQUE (source_key),
    CONSTRAINT data_sources_key_not_blank CHECK (length(trim(source_key)) > 0),
    CONSTRAINT data_sources_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT data_sources_type_valid CHECK (
        source_type IN ('DATASET', 'DOCUMENT', 'WEB_PAGE', 'API', 'USER_INPUT')
    ),
    CONSTRAINT data_sources_sha256_valid CHECK (
        content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT data_sources_metadata_object CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT data_sources_retrieved_after_published CHECK (
        published_at IS NULL OR retrieved_at >= published_at
    )
);

CREATE TABLE evidence_items (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES data_sources(id) ON DELETE RESTRICT,
    evidence_type VARCHAR(24) NOT NULL,
    claim_text TEXT NOT NULL,
    locator JSONB NOT NULL DEFAULT '{}'::jsonb,
    excerpt TEXT,
    measured_value JSONB,
    confidence NUMERIC(4,3) NOT NULL DEFAULT 1.000,
    observed_at TIMESTAMPTZ,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT evidence_items_type_valid CHECK (
        evidence_type IN ('FACT', 'MEASUREMENT', 'EXCERPT', 'TABLE_CELL', 'FIGURE', 'DERIVATION')
    ),
    CONSTRAINT evidence_items_claim_not_blank CHECK (length(trim(claim_text)) > 0),
    CONSTRAINT evidence_items_locator_object CHECK (jsonb_typeof(locator) = 'object'),
    CONSTRAINT evidence_items_payload_present CHECK (
        (excerpt IS NOT NULL AND length(trim(excerpt)) > 0) OR measured_value IS NOT NULL
    ),
    CONSTRAINT evidence_items_confidence_range CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT evidence_items_valid_period CHECK (valid_to IS NULL OR valid_from IS NOT NULL),
    CONSTRAINT evidence_items_valid_period_order CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE assumptions (
    id UUID PRIMARY KEY,
    assumption_key VARCHAR(160) NOT NULL,
    category VARCHAR(80) NOT NULL,
    statement TEXT NOT NULL,
    rationale TEXT NOT NULL,
    assumed_value JSONB,
    unit VARCHAR(80),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    confidence NUMERIC(4,3) NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT assumptions_key_unique UNIQUE (assumption_key),
    CONSTRAINT assumptions_key_not_blank CHECK (length(trim(assumption_key)) > 0),
    CONSTRAINT assumptions_category_not_blank CHECK (length(trim(category)) > 0),
    CONSTRAINT assumptions_statement_not_blank CHECK (length(trim(statement)) > 0),
    CONSTRAINT assumptions_rationale_not_blank CHECK (length(trim(rationale)) > 0),
    CONSTRAINT assumptions_status_valid CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT assumptions_confidence_range CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT assumptions_valid_period CHECK (valid_to IS NULL OR valid_from IS NOT NULL),
    CONSTRAINT assumptions_valid_period_order CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE provenance_links (
    id UUID PRIMARY KEY,
    subject_type VARCHAR(80) NOT NULL,
    subject_id UUID NOT NULL,
    property_path VARCHAR(240) NOT NULL,
    evidence_id UUID REFERENCES evidence_items(id) ON DELETE RESTRICT,
    assumption_id UUID REFERENCES assumptions(id) ON DELETE RESTRICT,
    transformation JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT provenance_links_subject_type_not_blank CHECK (length(trim(subject_type)) > 0),
    CONSTRAINT provenance_links_property_path_valid CHECK (property_path ~ '^(/([^/~]|~[01])*)*$'),
    CONSTRAINT provenance_links_exactly_one_origin CHECK (
        (evidence_id IS NOT NULL AND assumption_id IS NULL)
        OR (evidence_id IS NULL AND assumption_id IS NOT NULL)
    ),
    CONSTRAINT provenance_links_transformation_object CHECK (jsonb_typeof(transformation) = 'object')
);

CREATE INDEX data_sources_type_idx ON data_sources(source_type);
CREATE INDEX data_sources_retrieved_at_idx ON data_sources(retrieved_at);
CREATE INDEX evidence_items_source_id_idx ON evidence_items(source_id);
CREATE INDEX evidence_items_observed_at_idx ON evidence_items(observed_at);
CREATE INDEX assumptions_status_category_idx ON assumptions(status, category);
CREATE INDEX provenance_links_subject_idx ON provenance_links(subject_type, subject_id);
CREATE INDEX provenance_links_evidence_id_idx ON provenance_links(evidence_id) WHERE evidence_id IS NOT NULL;
CREATE INDEX provenance_links_assumption_id_idx ON provenance_links(assumption_id) WHERE assumption_id IS NOT NULL;
CREATE UNIQUE INDEX provenance_links_evidence_unique_idx
    ON provenance_links(subject_type, subject_id, property_path, evidence_id)
    WHERE evidence_id IS NOT NULL;
CREATE UNIQUE INDEX provenance_links_assumption_unique_idx
    ON provenance_links(subject_type, subject_id, property_path, assumption_id)
    WHERE assumption_id IS NOT NULL;

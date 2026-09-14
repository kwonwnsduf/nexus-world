CREATE TABLE ingestion_runs (
    id UUID PRIMARY KEY,
    source_system VARCHAR(32) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    request_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    pages_fetched INTEGER NOT NULL DEFAULT 0,
    raw_records INTEGER NOT NULL DEFAULT 0,
    normalized_records INTEGER NOT NULL DEFAULT 0,
    rejected_records INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    initiated_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ingestion_runs_source_valid CHECK (source_system IN (
        'SEC','OPENDART','UN_COMTRADE','WORLD_BANK','USGS','UNLOCODE','WPI','HS','ISIC',
        'UN_WPP','ILOSTAT','KOSIS','OECD'
    )),
    CONSTRAINT ingestion_runs_status_valid CHECK (status IN ('RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT ingestion_runs_request_object CHECK (jsonb_typeof(request_parameters) = 'object'),
    CONSTRAINT ingestion_runs_counts_nonnegative CHECK (
        pages_fetched >= 0 AND raw_records >= 0 AND normalized_records >= 0 AND rejected_records >= 0
    )
);

CREATE TABLE raw_ingestion_payloads (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ingestion_runs(id) ON DELETE RESTRICT,
    source_system VARCHAR(32) NOT NULL,
    request_uri TEXT NOT NULL,
    page_number INTEGER NOT NULL,
    media_type VARCHAR(160),
    content_encoding VARCHAR(40),
    source_version VARCHAR(120),
    retrieved_at TIMESTAMPTZ NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    response_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT raw_payload_page_positive CHECK (page_number > 0),
    CONSTRAINT raw_payload_sha_valid CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT raw_payload_metadata_object CHECK (jsonb_typeof(response_metadata) = 'object'),
    CONSTRAINT raw_payload_dedup UNIQUE (source_system, request_uri, content_sha256)
);

CREATE TABLE normalized_external_records (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ingestion_runs(id) ON DELETE RESTRICT,
    raw_payload_id UUID NOT NULL REFERENCES raw_ingestion_payloads(id) ON DELETE RESTRICT,
    source_system VARCHAR(32) NOT NULL,
    record_type VARCHAR(80) NOT NULL,
    natural_key VARCHAR(512) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    country_code VARCHAR(16),
    country_code_scheme VARCHAR(24),
    classification_code VARCHAR(80),
    classification_version VARCHAR(40),
    currency_code VARCHAR(12),
    unit_code VARCHAR(80),
    period_start DATE,
    period_end DATE,
    observed_at TIMESTAMPTZ,
    data_version VARCHAR(120),
    dimensions JSONB NOT NULL DEFAULT '{}'::jsonb,
    value JSONB NOT NULL,
    provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT normalized_fingerprint_valid CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT normalized_dimensions_object CHECK (jsonb_typeof(dimensions) = 'object'),
    CONSTRAINT normalized_provenance_object CHECK (jsonb_typeof(provenance) = 'object'),
    CONSTRAINT normalized_period_valid CHECK (period_end IS NULL OR period_start IS NOT NULL),
    CONSTRAINT normalized_period_order CHECK (period_end IS NULL OR period_end >= period_start),
    CONSTRAINT normalized_record_dedup UNIQUE (source_system, fingerprint)
);

CREATE TABLE ingestion_rejections (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ingestion_runs(id) ON DELETE RESTRICT,
    raw_payload_id UUID REFERENCES raw_ingestion_payloads(id) ON DELETE RESTRICT,
    record_locator VARCHAR(512) NOT NULL,
    error_code VARCHAR(80) NOT NULL,
    error_message TEXT NOT NULL,
    rejected_record JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ingestion_runs_source_started_idx ON ingestion_runs(source_system, started_at DESC);
CREATE INDEX raw_ingestion_payloads_run_idx ON raw_ingestion_payloads(run_id, page_number);
CREATE INDEX normalized_external_records_run_idx ON normalized_external_records(run_id);
CREATE INDEX normalized_external_records_lookup_idx ON normalized_external_records(source_system, record_type, country_code);
CREATE INDEX normalized_external_records_classification_idx ON normalized_external_records(classification_code, classification_version);
CREATE INDEX ingestion_rejections_run_idx ON ingestion_rejections(run_id);

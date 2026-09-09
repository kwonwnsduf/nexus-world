CREATE TABLE worlds (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT worlds_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT worlds_status_valid CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT worlds_revision_non_negative CHECK (revision >= 0)
);

CREATE TABLE world_versions (
    id UUID PRIMARY KEY,
    world_id UUID NOT NULL REFERENCES worlds(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL,
    state JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT world_versions_number_positive CHECK (version_number > 0),
    CONSTRAINT world_versions_world_number_unique UNIQUE (world_id, version_number)
);

CREATE TABLE scenarios (
    id UUID PRIMARY KEY,
    world_id UUID NOT NULL REFERENCES worlds(id) ON DELETE RESTRICT,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT scenarios_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT scenarios_world_name_unique UNIQUE (world_id, name)
);

CREATE TABLE scenario_branches (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES scenarios(id) ON DELETE RESTRICT,
    parent_branch_id UUID REFERENCES scenario_branches(id) ON DELETE RESTRICT,
    baseline_world_version_id UUID NOT NULL REFERENCES world_versions(id) ON DELETE RESTRICT,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT scenario_branches_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT scenario_branches_status_valid CHECK (
        status IN ('DRAFT', 'READY', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT scenario_branches_not_own_parent CHECK (parent_branch_id IS NULL OR parent_branch_id <> id),
    CONSTRAINT scenario_branches_scenario_name_unique UNIQUE (scenario_id, name)
);

CREATE TABLE simulation_runs (
    id UUID PRIMARY KEY,
    scenario_branch_id UUID NOT NULL REFERENCES scenario_branches(id) ON DELETE RESTRICT,
    seed BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT simulation_runs_status_valid CHECK (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT simulation_runs_started_after_request CHECK (started_at IS NULL OR started_at >= requested_at),
    CONSTRAINT simulation_runs_completed_after_start CHECK (
        completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at)
    )
);

CREATE INDEX world_versions_world_id_idx ON world_versions(world_id);
CREATE INDEX scenarios_world_id_idx ON scenarios(world_id);
CREATE INDEX scenario_branches_scenario_id_idx ON scenario_branches(scenario_id);
CREATE INDEX scenario_branches_parent_branch_id_idx ON scenario_branches(parent_branch_id);
CREATE INDEX simulation_runs_branch_status_idx ON simulation_runs(scenario_branch_id, status);

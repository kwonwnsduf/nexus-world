ALTER TABLE scenario_branches
    ADD COLUMN branch_input JSONB NOT NULL DEFAULT '{"shocks":[]}'::jsonb;

ALTER TABLE simulation_runs
    ADD COLUMN turns INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN request_hash CHAR(64),
    ADD COLUMN result_hash CHAR(64),
    ADD COLUMN final_state JSONB,
    ADD COLUMN invariant_results JSONB,
    ADD COLUMN failure_reason VARCHAR(1000),
    ADD CONSTRAINT simulation_runs_turns_bounded CHECK (turns BETWEEN 1 AND 120),
    ADD CONSTRAINT simulation_runs_request_hash_format CHECK (
        request_hash IS NULL OR request_hash ~ '^[a-f0-9]{64}$'
    ),
    ADD CONSTRAINT simulation_runs_result_hash_format CHECK (
        result_hash IS NULL OR result_hash ~ '^[a-f0-9]{64}$'
    );

CREATE TABLE turn_snapshots (
    id UUID PRIMARY KEY,
    simulation_run_id UUID NOT NULL REFERENCES simulation_runs(id) ON DELETE RESTRICT,
    turn INTEGER NOT NULL,
    state JSONB NOT NULL,
    metrics JSONB NOT NULL,
    invariant_violations JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT turn_snapshots_turn_positive CHECK (turn > 0),
    CONSTRAINT turn_snapshots_state_object CHECK (jsonb_typeof(state) = 'object'),
    CONSTRAINT turn_snapshots_metrics_object CHECK (jsonb_typeof(metrics) = 'object'),
    CONSTRAINT turn_snapshots_violations_array CHECK (
        jsonb_typeof(invariant_violations) = 'array'
    ),
    CONSTRAINT turn_snapshots_run_turn_unique UNIQUE (simulation_run_id, turn)
);

CREATE INDEX turn_snapshots_run_turn_idx ON turn_snapshots(simulation_run_id, turn);

CREATE OR REPLACE FUNCTION reject_immutable_world_version_update()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'world_versions are immutable; create a new version instead'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER world_versions_immutable
BEFORE UPDATE OR DELETE ON world_versions
FOR EACH ROW EXECUTE FUNCTION reject_immutable_world_version_update();

CREATE OR REPLACE FUNCTION validate_branch_parent_scope()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.parent_branch_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM scenario_branches parent
        WHERE parent.id = NEW.parent_branch_id AND parent.scenario_id = NEW.scenario_id
    ) THEN
        RAISE EXCEPTION 'parent branch must belong to the same scenario'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER scenario_branches_parent_scope
BEFORE INSERT OR UPDATE OF parent_branch_id, scenario_id ON scenario_branches
FOR EACH ROW EXECUTE FUNCTION validate_branch_parent_scope();

CREATE TABLE fault_experiments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    started_by      UUID NOT NULL,
    fault_key       VARCHAR(60) NOT NULL,
    target_service  VARCHAR(40) NOT NULL,
    param_ms        BIGINT,
    duration_seconds INT NOT NULL CHECK (duration_seconds BETWEEN 1 AND 120),
    status          VARCHAR(24) NOT NULL CHECK (status IN ('RUNNING','STOPPED','EXPIRED','FAILED_TO_START','DISABLE_UNCONFIRMED')),
    status_detail   TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);
CREATE INDEX idx_fault_exp_org ON fault_experiments (organization_id, started_at DESC);
-- Atomic guarantee: at most one active experiment per target service, even under concurrent requests.
-- DISABLE_UNCONFIRMED also blocks: the fault may still be on, so no new experiment until a disable is confirmed.
CREATE UNIQUE INDEX uq_fault_exp_one_active ON fault_experiments (target_service) WHERE status IN ('RUNNING','DISABLE_UNCONFIRMED');

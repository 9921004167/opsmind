-- Phase 6: AI investigation / RCA
-- NOTE: rename this file's version number if you already have a V4 (or later) in
-- your repo - check src/main/resources/db/migration/ for the current highest
-- version before copying this in. Flyway will fail to start if there's a
-- version collision or gap inconsistent with what's already been applied.

CREATE TABLE investigations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id       UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    failure_reason    TEXT,
    ai_provider       VARCHAR(50),
    ai_model          VARCHAR(100),
    started_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_investigations_incident_id ON investigations(incident_id, created_at DESC);
CREATE INDEX idx_investigations_organization_id ON investigations(organization_id);

CREATE TABLE evidence (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id  UUID NOT NULL REFERENCES investigations(id) ON DELETE CASCADE,
    type              VARCHAR(30) NOT NULL CHECK (type IN
                       ('METRIC','LOG','TRACE','DEPLOYMENT','CONFIG_CHANGE','DEPENDENCY','HISTORICAL_INCIDENT')),
    source            VARCHAR(50) NOT NULL,
    observed_at       TIMESTAMPTZ NOT NULL,
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    observed_value    VARCHAR(500),
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_evidence_investigation_id ON evidence(investigation_id, observed_at);

CREATE TABLE rca_findings (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id          UUID NOT NULL UNIQUE REFERENCES investigations(id) ON DELETE CASCADE,
    root_cause                TEXT NOT NULL,
    hypothesis                TEXT,
    confidence_level          VARCHAR(10) NOT NULL CHECK (confidence_level IN ('LOW','MEDIUM','HIGH')),
    confidence_score          DOUBLE PRECISION,
    affected_service          VARCHAR(255),
    impact                    TEXT,
    supporting_evidence_ids   JSONB NOT NULL,
    reasoning_summary         TEXT,
    recommended_actions       JSONB NOT NULL,
    model_provider            VARCHAR(50) NOT NULL,
    model_name                VARCHAR(100) NOT NULL,
    generated_at              TIMESTAMPTZ NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

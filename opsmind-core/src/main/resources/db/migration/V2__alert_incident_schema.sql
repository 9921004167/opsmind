-- Phase 1: alert ingestion + incident lifecycle schema

CREATE SEQUENCE incident_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE alerts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id        UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    environment_id    UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    service_id        UUID REFERENCES monitored_services(id) ON DELETE SET NULL,
    source            VARCHAR(100) NOT NULL,
    alert_type        VARCHAR(100) NOT NULL,
    severity          VARCHAR(20)  NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('RECEIVED','LINKED')),
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    metric_name       VARCHAR(255),
    threshold_value   NUMERIC,
    current_value     NUMERIC,
    raw_payload       JSONB,
    received_at       TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_organization_id ON alerts(organization_id);
CREATE INDEX idx_alerts_project_id ON alerts(project_id);
CREATE INDEX idx_alerts_received_at ON alerts(received_at DESC);

CREATE TABLE incidents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_number     VARCHAR(20) NOT NULL UNIQUE,
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id          UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    environment_id      UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    primary_service_id  UUID REFERENCES monitored_services(id) ON DELETE SET NULL,
    title               VARCHAR(500) NOT NULL,
    severity            VARCHAR(20) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status              VARCHAR(20) NOT NULL CHECK (status IN
                         ('DETECTED','NEW','INVESTIGATING','MITIGATING','AWAITING_APPROVAL',
                          'REMEDIATING','VERIFYING','RESOLVED','ESCALATED','CLOSED')),
    summary             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_incidents_organization_id ON incidents(organization_id);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_created_at ON incidents(created_at DESC);

CREATE TABLE incident_alerts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id          UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    alert_id             UUID NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
    correlation_reason   VARCHAR(100) NOT NULL,
    linked_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (incident_id, alert_id)
);
CREATE INDEX idx_incident_alerts_incident_id ON incident_alerts(incident_id);
CREATE INDEX idx_incident_alerts_alert_id ON incident_alerts(alert_id);

CREATE TABLE incident_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id   UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    event_type    VARCHAR(50) NOT NULL CHECK (event_type IN
                   ('INCIDENT_CREATED','STATUS_CHANGED','ALERT_LINKED','NOTE_ADDED')),
    description   TEXT,
    actor         VARCHAR(255) NOT NULL,
    metadata      JSONB,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_incident_events_incident_id ON incident_events(incident_id, occurred_at);

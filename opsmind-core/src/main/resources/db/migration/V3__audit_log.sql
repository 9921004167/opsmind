-- Phase 1: audit trail. Every automated and human-triggered action of consequence
-- (org registration, login, incident status change, Kafka events consumed) is
-- recorded here.

CREATE TABLE audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID REFERENCES organizations(id) ON DELETE CASCADE,
    actor_user_id     UUID REFERENCES app_users(id) ON DELETE SET NULL,
    actor_label       VARCHAR(100),
    action            VARCHAR(150) NOT NULL,
    entity_type       VARCHAR(100) NOT NULL,
    entity_id         VARCHAR(255) NOT NULL,
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_organization_id ON audit_logs(organization_id, created_at DESC);

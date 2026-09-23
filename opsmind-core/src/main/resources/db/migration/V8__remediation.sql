-- =====================================================================
-- Phase 8 : AI-assisted remediation, with a closed runbook catalog and
-- a fixed risk model. See project spec Section 8-19 for the safety rules
-- this schema exists to enforce.
-- =====================================================================

-- ---------------------------------------------------------------------
-- runbook_definitions
--   The CLOSED catalog Gemini may select from. Shared/global platform
--   data - deliberately has NO organization_id (Section 9).
-- ---------------------------------------------------------------------
CREATE TABLE runbook_definitions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key                 VARCHAR(100) NOT NULL UNIQUE,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    risk_level          VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    executor_type       VARCHAR(20) NOT NULL CHECK (executor_type IN ('FAULT_DISABLE','MANUAL')),
    execution_endpoint  TEXT,
    target_service_slug VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A FAULT_DISABLE runbook must have a real endpoint to call; a MANUAL
    -- runbook must never carry one it could accidentally be executed against.
    CONSTRAINT runbook_endpoint_matches_executor CHECK (
        (executor_type = 'FAULT_DISABLE' AND execution_endpoint IS NOT NULL)
        OR (executor_type = 'MANUAL' AND execution_endpoint IS NULL)
    )
);

-- ---------------------------------------------------------------------
-- remediation_recommendations
-- ---------------------------------------------------------------------
CREATE TABLE remediation_recommendations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id          UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    organization_id      UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    rca_finding_id       UUID REFERENCES rca_findings(id) ON DELETE SET NULL,
    runbook_id           UUID REFERENCES runbook_definitions(id),
    matched_runbook_key  VARCHAR(100),
    ai_suggested_key     VARCHAR(100),
    ai_recommendation_text TEXT,
    ai_rationale         TEXT,
    risk_level           VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    manual_only          BOOLEAN NOT NULL DEFAULT TRUE,
    status               VARCHAR(24) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN (
                              'PROPOSED','APPROVED','REJECTED','EXECUTING','EXECUTED',
                              'EXECUTION_FAILED','VERIFYING','VERIFIED_RECOVERED','VERIFICATION_FAILED')),
    ai_raw_response      JSONB,
    created_by_user_id   UUID REFERENCES app_users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,

    -- A runbook-backed recommendation is only ever safe to execute when
    -- manual_only is false; enforced again in application code, but the
    -- data itself should never contradict this.
    CONSTRAINT remediation_manual_only_consistency CHECK (
        manual_only = TRUE OR runbook_id IS NOT NULL
    )
);
CREATE INDEX idx_remediation_recommendations_incident ON remediation_recommendations(incident_id, organization_id);
CREATE INDEX idx_remediation_recommendations_org_status ON remediation_recommendations(organization_id, status);

-- ---------------------------------------------------------------------
-- remediation_approvals
-- ---------------------------------------------------------------------
CREATE TABLE remediation_approvals (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id UUID NOT NULL REFERENCES remediation_recommendations(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    decision          VARCHAR(10) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
    actor_user_id     UUID REFERENCES app_users(id),
    auto_approved     BOOLEAN NOT NULL DEFAULT FALSE,
    reason            TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A human decision always has an actor; only an auto-approval may omit one.
    CONSTRAINT remediation_approval_actor_chk CHECK (
        auto_approved = TRUE OR actor_user_id IS NOT NULL
    )
);
CREATE INDEX idx_remediation_approvals_recommendation ON remediation_approvals(recommendation_id);

-- ---------------------------------------------------------------------
-- remediation_executions
-- ---------------------------------------------------------------------
CREATE TABLE remediation_executions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id UUID NOT NULL REFERENCES remediation_recommendations(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    executor_type     VARCHAR(20) NOT NULL CHECK (executor_type IN ('FAULT_DISABLE','MANUAL')),
    succeeded         BOOLEAN NOT NULL,
    detail            TEXT,
    actor_user_id     UUID NOT NULL REFERENCES app_users(id),
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_remediation_executions_recommendation ON remediation_executions(recommendation_id);

-- ---------------------------------------------------------------------
-- remediation_verifications
-- ---------------------------------------------------------------------
CREATE TABLE remediation_verifications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id UUID NOT NULL REFERENCES remediation_recommendations(id) ON DELETE CASCADE,
    organization_id   UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    result            VARCHAR(16) NOT NULL CHECK (result IN ('RECOVERED','NOT_RECOVERED','INCONCLUSIVE')),
    metric_name       VARCHAR(100),
    observed_value    VARCHAR(50),
    detail            TEXT,
    verified_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_remediation_verifications_recommendation ON remediation_verifications(recommendation_id);

-- ---------------------------------------------------------------------
-- Seed catalog. Endpoints use the docker-compose service DNS names.
-- product-catalog-service has no fault-injection endpoints, so it has no
-- FAULT_DISABLE runbook - only the three services Phase 4 wired faults
-- into (inventory, payment, order) get one.
-- ---------------------------------------------------------------------
INSERT INTO runbook_definitions (key, title, description, risk_level, executor_type, execution_endpoint, target_service_slug) VALUES
('disable-inventory-fault', 'Disable injected fault on inventory-service',
 'Calls the Phase 4 fault-disable endpoint to turn off active fault injection on inventory-service.',
 'LOW', 'FAULT_DISABLE', 'http://inventory-service:8092/faults/inventory/disable', 'inventory-service'),
('disable-payment-fault', 'Disable injected fault on payment-service',
 'Calls the Phase 4 fault-disable endpoint to turn off active fault injection on payment-service.',
 'LOW', 'FAULT_DISABLE', 'http://payment-service:8091/faults/payment/disable', 'payment-service'),
('disable-order-fault', 'Disable injected fault on order-service',
 'Calls the Phase 4 fault-disable endpoint to turn off active fault injection on order-service.',
 'LOW', 'FAULT_DISABLE', 'http://order-service:8093/faults/order/disable', 'order-service'),
('restart-service-pod', 'Restart the affected service instance',
 'Restart the container/pod running the affected service. No automated executor exists yet - perform manually.',
 'MEDIUM', 'MANUAL', NULL, NULL),
('scale-out-service', 'Scale out the affected service',
 'Increase replica count to relieve load/latency. No automated executor exists yet - perform manually.',
 'MEDIUM', 'MANUAL', NULL, NULL),
('rollback-deployment', 'Roll back the most recent deployment',
 'Revert the affected service to its previous known-good deployment. No automated executor exists yet - perform manually.',
 'HIGH', 'MANUAL', NULL, NULL),
('failover-database', 'Fail over the primary database',
 'Promote a database replica / trigger failover. No automated executor exists yet - perform manually.',
 'HIGH', 'MANUAL', NULL, NULL);

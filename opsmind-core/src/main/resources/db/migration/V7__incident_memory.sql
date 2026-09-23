-- =====================================================================
-- Phase 7 : RAG / Organizational Incident Memory
-- PostgreSQL + pgvector
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------
-- incident_memory
--   One row per RESOLVED incident. Doubles as the embedding work queue:
--   status PENDING -> PROCESSING -> READY (or FAILED, with backoff).
-- ---------------------------------------------------------------------
CREATE TABLE incident_memory (
    id                UUID PRIMARY KEY,
    organization_id   UUID NOT NULL,
    incident_id       UUID NOT NULL UNIQUE
                          REFERENCES incidents (id) ON DELETE CASCADE,

    -- the text that actually gets embedded
    content           TEXT        NOT NULL,
    content_hash      CHAR(64)    NOT NULL,

    -- 1536 dims: indexable by HNSW (pgvector caps `vector` indexes at 2000).
    -- gemini-embedding-2 defaults to 3072 and supports MRL truncation.
    embedding         vector(1536),
    embedding_model   VARCHAR(64) NOT NULL,

    -- denormalized so retrieval can filter/rerank without joining incidents
    service_names     TEXT[]      NOT NULL DEFAULT '{}',
    severity          VARCHAR(16),
    resolved_at       TIMESTAMPTZ NOT NULL,

    -- queue state
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts          INT         NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error        TEXT,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT incident_memory_status_chk
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),

    -- A READY row without a vector would silently poison retrieval.
    CONSTRAINT incident_memory_ready_has_vector
        CHECK (status <> 'READY' OR embedding IS NOT NULL)
);

-- worker claim scan
CREATE INDEX idx_memory_queue
    ON incident_memory (next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

-- tenant prefilter / housekeeping
CREATE INDEX idx_memory_org
    ON incident_memory (organization_id, status, resolved_at DESC);

-- ANN index. Cosine, because embeddings are L2-normalized on write.
CREATE INDEX idx_memory_embedding
    ON incident_memory USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ---------------------------------------------------------------------
-- incident_memory_feedback
--   Records which historical incidents were surfaced for which live
--   incident, and whether they turned out to be useful. Phase 7 only
--   writes this; a later phase can use it to rerank or to prune memory.
-- ---------------------------------------------------------------------
CREATE TABLE incident_memory_feedback (
    id                    UUID PRIMARY KEY,
    organization_id       UUID NOT NULL,
    query_incident_id     UUID NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    retrieved_incident_id UUID NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    rank                  INT              NOT NULL,
    similarity            DOUBLE PRECISION NOT NULL,
    final_score           DOUBLE PRECISION NOT NULL,
    used_in_rca           BOOLEAN          NOT NULL DEFAULT FALSE,
    was_helpful           BOOLEAN,
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT uq_memory_feedback
        UNIQUE (query_incident_id, retrieved_incident_id)
);

CREATE INDEX idx_memory_feedback_org
    ON incident_memory_feedback (organization_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Optional defense-in-depth: row level security.
-- Enable only if your app already sets a per-request GUC for the tenant.
-- The application code filters by organization_id regardless; this is a
-- second lock on the same door.
-- ---------------------------------------------------------------------
-- ALTER TABLE incident_memory ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY incident_memory_tenant_isolation ON incident_memory
--     USING (organization_id = current_setting('app.organization_id')::uuid);

-- AI-memory aggregate (F-02): the first domain aggregate, and so the first use of the
-- UUID v7 surrogate-PK + timestamptz audit-column conventions. Three tables — an
-- ai_memory root (one row per user) owning a semantic profile (ai_memory_profile_fact,
-- durable typed facts) and a bounded episodic log (ai_memory_episode, generic
-- event_type + jsonb payload). Pure CREATE — backward-compatible (expand-only); safe
-- under an image rollback. No writers in F-02; rows arrive with S-03/S-04.

CREATE TABLE ai_memory (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
-- DEFERRED FK (intentional, not an oversight): ai_memory.user_id has no FK to user(id)
-- yet — the user table arrives in S-01 (account-and-auth), which adds the constraint via
-- an expand-only ALTER. The UNIQUE above already enforces one memory per user, and the
-- unique index it creates serves the findByUserId lookup (no separate index needed).

CREATE TABLE ai_memory_profile_fact (
    id           UUID        PRIMARY KEY,
    ai_memory_id UUID        NOT NULL REFERENCES ai_memory (id),
    kind         VARCHAR(64) NOT NULL,
    content      TEXT        NOT NULL,
    provenance   VARCHAR(255),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE ai_memory_episode (
    id           UUID        PRIMARY KEY,
    ai_memory_id UUID        NOT NULL REFERENCES ai_memory (id),
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

-- Postgres does not auto-index FK columns; index the two child FKs so aggregate loads
-- (root → children) and future cascade checks stay index-backed.
CREATE INDEX idx_ai_memory_profile_fact_memory ON ai_memory_profile_fact (ai_memory_id);
CREATE INDEX idx_ai_memory_episode_memory ON ai_memory_episode (ai_memory_id);

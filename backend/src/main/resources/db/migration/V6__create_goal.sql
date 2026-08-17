-- Goals and dreams (S-02, FR-004/FR-005): one table for both non-task layers, discriminated by
-- `layer` (GOAL = long-term, horizon required; DREAM = someday, horizon forbidden) rather than two
-- tables — S-04/S-05/S-08/S-09 all consume the union and nothing but the horizon differs.
-- Pure CREATE — backward-compatible (expand-only); safe under an image rollback.
--
-- NO ON DELETE CASCADE on user_id, matching V5: FR-019 account deletion is app-orchestrated
-- (AccountDeletionService walks the PerUserDataDeleter registry), and the plain FK is the DB
-- backstop that makes a forgotten deleter fail loudly instead of orphaning rows.

CREATE TABLE goal (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES app_user (id),
    content       VARCHAR(500) NOT NULL,
    layer         VARCHAR(16)  NOT NULL,
    horizon       VARCHAR(16),
    category_code VARCHAR(32)  REFERENCES category (code),
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    -- The layer x horizon invariant, unbypassable. Also enforced in the aggregate constructor and
    -- in the request DTOs (-> 422); this is the backstop that holds for any future writer.
    CONSTRAINT chk_goal_layer_horizon CHECK (
        (layer = 'GOAL'  AND horizon IS NOT NULL) OR
        (layer = 'DREAM' AND horizon IS NULL)
    )
);

-- Postgres does not auto-index FK columns; user_id backs every scoped read, category_code backs
-- the FK check and S-08's per-domain view.
CREATE INDEX idx_goal_user ON goal (user_id);
CREATE INDEX idx_goal_category ON goal (category_code);

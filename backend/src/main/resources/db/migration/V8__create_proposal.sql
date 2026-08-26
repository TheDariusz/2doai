-- The proactive loop's memory (S-04b, FR-013/FR-018): one row per proposal the user was actually
-- shown, and what they answered. Persisted rather than computed on the fly for two reasons — a
-- second press of the "give me something now" button must return the same proposal instead of
-- paying for a second model call, and FR-018's at-most-one-pending rule needs somewhere to be true.
--
-- Expand-only and safe under an image rollback: the table is inert to the previous image, and both
-- new `goal` columns are nullable so it still inserts.
--
-- NO ON DELETE CASCADE on user_id, matching V5/V6: FR-019 account deletion is app-orchestrated and
-- the plain FK is the backstop that makes a forgotten deleter fail loudly.

CREATE TABLE proposal (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES app_user (id),
    -- ON DELETE CASCADE here, unlike every other FK in this schema, so DELETE /api/goals/{id} keeps
    -- working while a proposal points at the entry. The cost is named rather than hidden: because
    -- GoalDataDeleter runs during account deletion and every proposal has a goal, this cascade
    -- erases a user's proposals before ProposalDataDeleter is reached — so the "a missing deleter
    -- fails loudly on the FK" property does NOT protect this table. The deleter exists anyway; it
    -- becomes load-bearing the moment a proposal can outlive its entry.
    goal_id        UUID        NOT NULL REFERENCES goal (id) ON DELETE CASCADE,
    -- The phrased proposal, kept so a reload shows the sentence the user already read. TEXT, not a
    -- capped VARCHAR: a model writes it, and truncating a generated sentence mid-word is worse than
    -- storing a long one.
    message        TEXT        NOT NULL,
    -- The engine's reason, frozen at the moment of phrasing — the message spells this number out in
    -- words, so recomputing it later would leave the prose and the number disagreeing.
    neglected_days INTEGER     NOT NULL,
    -- LLM or TEMPLATE: which arm produced the message. A demo has to be able to tell a real Sonnet
    -- proposal from the fallback the LlmException catch arm writes.
    source         VARCHAR(16) NOT NULL,
    answer         VARCHAR(16),
    answered_at    TIMESTAMPTZ,
    -- FR-014's 3-5 bullets, as {"steps": [...]}. Stored so a reload shows the same plan instead of
    -- quietly generating a different one; jsonb like ai_memory_episode.payload, which the mapping
    -- follows too (a raw JSON String, no Jackson coupling in the entity).
    first_step     JSONB,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

-- Postgres does not auto-index FK columns; user_id backs every scoped read and the FR-019 erasure,
-- goal_id backs the cascade check above.
CREATE INDEX idx_proposal_user ON proposal (user_id);
CREATE INDEX idx_proposal_goal ON proposal (goal_id);

-- FR-018 as a schema invariant. A service-level "does this user already have one?" would race with
-- itself on a double-click and store both; a partial unique index cannot. Partial on purpose:
-- answered rows accumulate freely, only the pending slot is exclusive.
CREATE UNIQUE INDEX idx_proposal_one_pending ON proposal (user_id) WHERE answered_at IS NULL;

-- The two states an *answer* leaves on the entry itself. They live on `goal`, not on `proposal`,
-- because the user performed them — which is what keeps ProposalSelector's "last interaction is
-- goal.updated_at" reading honest (see its javadoc). Anything the machine writes on its own stays
-- on the proposal row above.
--
-- remind_after is a DATE, compared against the user's local date the way due_date already is: a
-- snooze is "come back on Thursday", not a moment in a timezone.
ALTER TABLE goal ADD COLUMN remind_after DATE;
ALTER TABLE goal ADD COLUMN withdrawn_at TIMESTAMPTZ;

-- Long sessions (passwordless sign-in, release 1). A remember-me cookie names one row here, so a user
-- stays signed in across a browser restart and a backend deploy, both of which lose the in-memory
-- HttpSession. This is NOT a session store: the row is read only on a request that arrives with no
-- live session (once per browser launch or backend restart, per device), never on an ordinary request,
-- so Neon still suspends with a signed-in tab left open. Decision record:
-- context/foundation/auth-session-model.md (addendum of 2026-11-14).
--
-- series names the row and is what the cookie looks it up by; token_value is the secret half, compared
-- in constant time. The value never rotates (see StableTokenRememberMeServices for why), so the only
-- write on an auto-login is last_used_at, which is also what the 90-day expiry counts from. Expired rows
-- are deleted when their owner next signs in; there is no cleanup job (lessons.md, Neon idleness).
--
-- Pure CREATE — expand-only, safe under an image rollback (the previous image never reads it).
-- NO ON DELETE CASCADE on user_id, matching V5/V6: FR-019 erasure goes through the PerUserDataDeleter
-- registry, and the plain FK makes a forgotten deleter fail loudly instead of orphaning rows.

CREATE TABLE remember_me_token (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES app_user (id),
    series       VARCHAR(64)  NOT NULL,
    token_value  VARCHAR(64)  NOT NULL,
    last_used_at TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_remember_me_token_series UNIQUE (series)
);

-- Postgres does not auto-index FK columns; user_id backs logout, deletion and the expiry sweep.
CREATE INDEX idx_remember_me_token_user ON remember_me_token (user_id);

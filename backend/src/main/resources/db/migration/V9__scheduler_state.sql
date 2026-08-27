-- Per-user timing state for the natural rhythm (S-05, FR-011/FR-018): the one moment the scheduler
-- has to survive a restart with.
--
-- Why a column at all, when the whole schedule lives in memory: because the rhythm is the product
-- claim ("a friend, not a cron"), and a schedule that reset on every deploy would bunch proposals
-- around restarts — the one pattern a user would read as mechanical. One timestamp per user is the
-- entire persistent state; the in-memory map is a mirror of it, written only when a fire actually
-- does database work anyway (lessons.md: nothing may touch Neon more often than its ~5-min
-- autosuspend window, so the write has to piggyback on work already happening).
--
-- Nullable, and that is the backfill: null means "never scheduled yet", which the scheduler draws a
-- first fire time for at boot and on registration. Expand-only and safe under an image rollback —
-- the previous image simply never reads it.
--
-- No change to `proposal`: S-05's SUPERSEDED is a fifth value in the existing answer VARCHAR(16),
-- and because it is written together with answered_at it already satisfies
-- proposal_answer_is_whole.

ALTER TABLE app_user ADD COLUMN next_proposal_at TIMESTAMPTZ;

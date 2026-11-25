-- Close the proposals left waiting on entries the user had already completed or withdrawn, before
-- GoalService started announcing that transition (GoalLeftTheRunning). From this version on the
-- closure happens in the same transaction as the completion; this sweeps the rows written before it.
--
-- Data-only and idempotent: it touches pending rows alone, so a re-run finds nothing. ENTRY_CLOSED
-- fits answer VARCHAR(16), and writing answer and answered_at together keeps
-- proposal_answer_is_whole satisfied. Under an image rollback the previous image never loads these
-- rows: it only reads pending proposals, or a proposal by the id a pending card carried.
UPDATE proposal p
SET answer      = 'ENTRY_CLOSED',
    answered_at = now(),
    updated_at  = now()
FROM goal g
WHERE g.id = p.goal_id
  AND p.answered_at IS NULL
  AND (g.completed_at IS NOT NULL OR g.withdrawn_at IS NOT NULL);

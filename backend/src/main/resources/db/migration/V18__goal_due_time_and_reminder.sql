-- DEV-63: a task may carry a time of day beside its due date, a per-task reminder lead, and the
-- marker of the one reminder e-mail its current term has produced.
--
-- Expand-only: all three columns are nullable, so the previous image — which maps none of them —
-- still inserts, and every row it creates (no time, no lead) satisfies the widened CHECK below. The
-- one rollback edge (an old-image edit that clears the date of a timed task) is described in the
-- change's plan under Migration Notes.

ALTER TABLE goal ADD COLUMN due_time TIME;
ALTER TABLE goal ADD COLUMN reminder_lead VARCHAR(16);

-- Written by the reminder fire only, through a bulk update that leaves updated_at alone: the proposal
-- selector reads updated_at as the user's last engagement, and machine bookkeeping must not reset it.
ALTER TABLE goal ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- Layer-major, as in V7, so a layer the enum gains but this constraint has not learned fails closed.
-- A time only beside a date, and a time and a lead always together — a lead with no time would be a
-- reminder with nothing to lead.
ALTER TABLE goal DROP CONSTRAINT chk_goal_layer_time_fields;
ALTER TABLE goal ADD CONSTRAINT chk_goal_layer_time_fields CHECK (
    (layer = 'GOAL'  AND horizon IS NOT NULL AND due_date IS NULL
                     AND due_time IS NULL AND reminder_lead IS NULL) OR
    (layer = 'DREAM' AND horizon IS NULL     AND due_date IS NULL
                     AND due_time IS NULL AND reminder_lead IS NULL) OR
    (layer = 'TASK'  AND horizon IS NULL
                     AND (due_time IS NULL OR due_date IS NOT NULL)
                     AND ((due_time IS NULL) = (reminder_lead IS NULL)))
);

ALTER TABLE goal ADD CONSTRAINT chk_goal_reminder_lead CHECK (
    reminder_lead IN ('AT_TIME', 'FIFTEEN_MINUTES', 'ONE_HOUR', 'MORNING_OF')
);

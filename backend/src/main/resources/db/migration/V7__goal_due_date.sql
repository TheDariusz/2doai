-- The third layer (S-07, FR-003): current tasks join goals and dreams on the same table as a third
-- `layer` value plus one nullable column, rather than a parallel `task` aggregate — S-04/S-08/S-09/
-- S-10 all consume the union and only the time fields differ. The split is worth making when tasks
-- get a different lifecycle (recurrence, overdue alarms), and then it is a migration, not a rewrite.
--
-- Expand-only and safe under an image rollback, in both directions:
--   * `due_date` is nullable, so the previous image — which does not map the column — still inserts;
--   * the widened CHECK is strictly weaker than the one it replaces, so every row that image writes
--     still satisfies it.

ALTER TABLE goal ADD COLUMN due_date DATE;

-- Widened under the same name, not joined by a second constraint: one predicate spanning all three
-- layers is what makes "a GOAL never carries a due date" as unbypassable as "a DREAM never carries a
-- horizon" already was. Postgres cannot alter a CHECK expression in place, hence drop then add.
ALTER TABLE goal DROP CONSTRAINT chk_goal_layer_horizon;
ALTER TABLE goal ADD CONSTRAINT chk_goal_layer_horizon CHECK (
    (layer = 'GOAL'  AND horizon IS NOT NULL AND due_date IS NULL) OR
    (layer = 'DREAM' AND horizon IS NULL     AND due_date IS NULL) OR
    (layer = 'TASK'  AND horizon IS NULL)
);

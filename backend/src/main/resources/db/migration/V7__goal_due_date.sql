-- The third layer (S-07, FR-003): a third `layer` value plus one nullable column on the existing
-- table, rather than a parallel `task` aggregate. Why one aggregate rather than two: see Goal's
-- javadoc, which owns that argument.
--
-- Expand-only and safe under an image rollback, in both directions:
--   * `due_date` is nullable, so the previous image — which does not map the column — still inserts;
--   * the widened CHECK is strictly weaker than the one it replaces, so every row that image writes
--     still satisfies it.

ALTER TABLE goal ADD COLUMN due_date DATE;

-- One predicate spanning all three layers rather than a second constraint beside the old one: that
-- is what makes "a GOAL never carries a due date" as unbypassable as "a DREAM never carries a
-- horizon" already was. Renamed along with it — the rule stopped being about horizons alone, and
-- this name is what a raw-SQL writer reads in the error. Postgres cannot alter a CHECK expression in
-- place, hence drop then add.
--
-- Deliberately layer-major (one disjunct per layer) where Goal.hasConsistentTimeFields is
-- field-major: a backstop should fail closed, so a `layer` the enum gains but this constraint has
-- not learned is rejected outright rather than silently admitted carrying no time fields at all.
ALTER TABLE goal DROP CONSTRAINT chk_goal_layer_horizon;
ALTER TABLE goal ADD CONSTRAINT chk_goal_layer_time_fields CHECK (
    (layer = 'GOAL'  AND horizon IS NOT NULL AND due_date IS NULL) OR
    (layer = 'DREAM' AND horizon IS NULL     AND due_date IS NULL) OR
    (layer = 'TASK'  AND horizon IS NULL)
);

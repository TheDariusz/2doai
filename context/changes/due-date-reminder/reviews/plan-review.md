<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Task Time-of-Day and Due-Date Reminder (DEV-63)

- **Plan**: context/changes/due-date-reminder/plan.md
- **Mode**: Deep, looped. Review → fix → re-review, 5 iterations (iterations 3–5 were independent reviewers that did not see earlier conclusions).
- **Date**: 2026-11-26
- **Verdict**: SOUND (iteration 5)
- **Findings (final iteration)**: 0 critical, 0 warnings, 4 observations (all fixed)

## Verdicts (final)

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | PASS |

## Grounding
7/7 paths ✓, 4/4 symbols ✓, brief↔plan ✓ (brief updated with grace, entry shape, updated_at risk)

## Iteration history

### Iteration 1: REVISE (6 warnings, 3 observations). All were fixed, using the recommended fix.
- F1 Phase-1 edit form would wipe due_time/reminder_lead (`submit()` builds from FormData). **Decision**: FIXED. Both write paths carry the fields.
- F2 Reminder mail ignored the shared Resend 100/day allowance. **Decision**: FIXED. Documented in Performance, no cap.
- F3 Unconditional removal after the fire could erase a re-armed entry. **Decision**: FIXED. Conditional `remove(goalId, entry)` before the fire.
- F4 An in-transaction `@EventListener` could lose a reminder on rollback. **Decision**: FIXED. `@TransactionalEventListener(AFTER_COMMIT)`.
- F5 Writes outside GoalService (the proposal NEVER answer) emit no event. **Decision**: FIXED. Stated and accepted, and the re-check covers it.
- F6 The Goal constructor change broke about 25 test call sites, and the USER_ZONE destination was unnamed. **Decision**: FIXED. The 6-argument overload is kept, and `user/UserZone` is added with an alias.
- F7 Criterion 2.3 was an "either/or". **Decision**: FIXED. It now uses `HealthProbeApiTest`.
- F8 The phase 3 cascade needs controlled date/time state. **Decision**: FIXED.
- F9 The home of the fire transaction and the return of `Goal.update` were left open. **Decision**: FIXED.

### Iteration 2: REVISE (2 warnings). Both were fixed.
- F1 The claim that a rollback is safe was false for an old image editing a timed task. **Decision**: FIXED. The edge is documented, with a recovery SQL.
- F2 Phase-1 `submit()` echoed the time after the date was cleared or the layer switched (422). **Decision**: FIXED.

### Iteration 3: REVISE (1 critical, 2 warnings, 3 observations). All were fixed.
- F1 AT_TIME's moment equals its expiry, so it could never fire, and overdue tasks would mail when reopened. **Decision**: FIXED. Expiry = due moment + `reminder.late-grace`, and the listener and boot load skip expired terms.
- F2 A failed fire transaction lost the reminder. **Decision**: FIXED. It re-arms with `notBefore`, bounded by expiry.
- F3 The real tick runs in the shared test context. **Decision**: FIXED. Test rules are stated.
- F4 The create event needs the id from after the save. F5 Criterion 4.3 was not attributable to sections. F6 Criterion 3.4 needed a verified session. **Decision**: FIXED.

### Iteration 4: REVISE (1 critical, 1 warning, 3 observations). All were fixed.
- F1 Stamping `reminder_sent_at` through the entity moves `updated_at`, which is the rhythm's neglect clock. **Decision**: FIXED. Stamping is a bulk `@Modifying` update, and the test asserts `updated_at` is unchanged.
- F2 A stale map entry dropped the real reminder. **Decision**: FIXED. It re-arms to the current window, and the boot load uses `putIfAbsent`.
- F3 The retry delay equalled the grace, and the grace floor ignored the shared scheduler thread. **Decision**: FIXED. The retry is 1 min and the floor is PT4M.
- F4 A lead crossing midnight had no test. F5 The boot finder now returns a projection. **Decision**: FIXED.

### Iteration 5: SOUND (4 observations). All were fixed.
- The `FireOutcome` return type is stated, Critical Details uses `max(moment, notBefore)`, criterion 4.3 has a term per section, and the grace-floor reference now points at the timeout properties. **Decision**: FIXED.

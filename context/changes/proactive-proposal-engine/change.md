---
change_id: proactive-proposal-engine
title: S-04b — LLM-phrased proposal, four responses, first step
status: implementing
created: 2026-08-25
updated: 2026-08-25
archived_at: null
---

## Notes

- Linear: DEV-23 (S-04b). PRD refs FR-012/FR-013/FR-014/FR-015. S-04a (DEV-46) shipped the
  deterministic half as `ProposalSelector` + `POST /api/proposals`; this change puts an LLM in
  front of it and builds the user-facing loop.
- **This is `LlmClient`'s first production caller.** Until now the OpenRouter path has only ever
  been exercised by a gated live test.

### Decisions taken 2026-08-25 (with the author, before planning)

- **The proposal is persisted, not ephemeral.** Scope deliberately extended — the slice is running
  nine days ahead of its 09-03 window, so FR-018's at-most-one-pending rule is enforced now rather
  than retrofitted by S-05. New `proposal` table; S-05 inherits it along with the scheduler.
- **A second click returns the pending proposal unchanged, with no second LLM call.** That is what
  at-most-one means under a manual trigger, and it stops button-mashing from burning Sonnet calls.
  The PRD's "unanswered = implicit *nie teraz*" applies when a *cycle* supersedes a proposal, which
  arrives with S-05's scheduler.
- **`remind_after` mechanism is in.** The 7/30/90 presets write it; *nie teraz* is a short snooze on
  the same column. Two buttons, one mechanism, different defaults — not two names for one thing.
- **"Wycofane" is a third filter value in the S-08 filter row**, not its own route. Withdrawal is
  another answer to "which entries am I looking at", which is what that row already owns; a route
  would mean a second component rendering an entry list. Promote it later if it grows bulk restore
  or its own sorting.
- **The 08.09 gate's text-template fallback is built now, not held in reserve.** It is the
  `LlmException` catch arm, so it makes the whole loop testable without a live model — and if the
  gate ever fires, nothing has to be written under time pressure.

### Constraint inherited from S-04a

`ProposalSelector`'s javadoc warns that "last interaction" is `goal.updated_at`, and that S-04b must
not stamp that row with bookkeeping the user did not perform — or must move the clock to a
`last_interaction_at` column. Snoozing and withdrawing *are* user-performed, so writing them to the
`goal` row keeps that promise. Anything the machine writes on its own (proposal shown, message
cached) belongs on the `proposal` row instead.

### Adaptation taken during Phase 1 (2026-08-25)

- **`GoalUpdate.withdrawn` deferred from Phase 1 to Phase 4.** Jackson 3 (Boot 4) enables
  `FAIL_ON_NULL_FOR_PRIMITIVES` by default, so a new primitive on a request record is a *breaking*
  wire change: `PUT /api/goals/{id}` without the field answers 400, not "false". The plan's premise
  ("an omitted field means active") is not how the stack actually behaves. The field therefore lands
  with the clients that send it — the SPA's `replace()` and the five `toEqual` PUT-body assertions in
  `GoalsPage.test.tsx`, all of which Phase 4 rewrites anyway. Nothing in between needs it: Phase 3's
  `NEVER` answer calls `Goal#withdraw` from the service, not through `PUT`. `Goal#restore` is
  therefore unused in production code until Phase 4 (it is covered by `GoalPersistenceTest`).
- **`Goal`'s two new response fields went into `openapi.yaml` in Phase 1**, not Phase 2 as planned —
  CLAUDE.md's rule is that a wire literal moves spec + Java together, and the response publishes them
  from this phase on. The `GoalUpdate` description was corrected in the same pass: it claimed an
  absent `completed` means active, which the 400 above disproves.

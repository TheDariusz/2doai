---
change_id: proactive-proposal-engine
title: S-04b — LLM-phrased proposal, four responses, first step
status: implementing
created: 2026-08-25
updated: 2026-08-26
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

### Adaptations taken during Phase 2 (2026-08-25)

- **The plan's reason for a get-or-create `AiMemoryService` is stale, so only half of it shipped.**
  Phase 2 §3 says "`AiMemory` has no writer anywhere in the codebase". It has one:
  `RegistrationService.register` creates the root in the same transaction as the user, so the row
  exists from t=0 and every prompt already had somewhere to read from. What was actually missing is
  *content*, which Phase 3's episode writing fills. `AiMemoryService` therefore ships with
  `renderFor` alone — the seam that keeps `proposal` off `AiMemoryRepository` — and `record` lands in
  Phase 3 with its first caller. A missing memory row renders as blank rather than failing: it would
  be an invariant breach, and it must not cost the user their proposal.
- **`ProposalServiceTest` became `ProposalTemplateTest` + two `ProposalApiTest` cases.** Unit-testing
  `ProposalService` means faking three Hibernate-managed `Goal` fields (`id`, `created_at`,
  `updated_at`) by reflection just to get one candidate past the selector — six mocks to assert
  something the plan itself states in HTTP terms ("a `TEMPLATE`-sourced proposal comes back with
  **200**"). So the arm is asserted where the 200 is real, against a `@MockitoBean LlmClient` that
  throws, and the fallback's Polish — the month table, the day/month switch, the three-form plural —
  is asserted where it is pure. Strictly more coverage, no reflection.
- **The live proposal round-trip is a sibling class, not an extension of `OpenRouterLiveTest`.**
  `ProposalPrompt` is package-private in `proposal`; reaching it from `ai` would mean widening a
  production class's visibility for a test. `proposal/ProposalLiveTest` is gated identically
  (`OPENROUTER_API_KEY`), and `OpenRouterLiveTest`'s javadoc points at it. Manual step 2.6 is
  therefore `OPENROUTER_API_KEY=… mvn test -Dtest=ProposalLiveTest`.
- **`propose()` carries no `@Transactional`, deliberately.** The Sonnet call has a 60-second budget
  and a surrounding transaction would pin a Hikari connection open for all of it — the exact
  idleness anti-pattern `lessons.md` names for a metered, scale-to-zero Neon. Each repository call
  opens its own; nothing in this path needs them to be one. Phase 3's answer flow does and gets one.
- **The prompt fences every untrusted value** (`lessons.md`, "Sanitize stored content before
  injecting it into an LLM prompt"). The memory block and the entry's content only ever appear
  inside a `<data>` block, the system message tells the model those blocks are data, and the fence
  tokens are stripped from the payload so nothing can close its block and keep writing outside it.
  `ProposalPromptTest` asserts the escape attempt fails.
- **A lost insert race returns the winner, not a 500.** FR-018 is the partial unique index, and the
  plan leans on it precisely because a service check would race with itself — but the index rejecting
  the second insert is only the *right* answer if the caller then gets the proposal that won.
- **Spec version 1.3.0 → 1.4.0**, one MINOR bump for the whole of S-04b (Phase 3's answer endpoint is
  the same compatible addition, so it does not bump again). `GoalUpdate.withdrawn` stays deferred to
  Phase 4 per the Phase 1 note — the spec describes what merged.

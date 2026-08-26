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

### Adaptations taken during Phase 3 (2026-08-26)

- **The prompts were switched from Polish to English, and the rule written into `CLAUDE.md`.** Phase
  2 shipped `ProposalPrompt` with its persona written in the language the answer had to come back in,
  and Phase 3 followed that convention for the first-step persona. Reviewed by the author, the
  convention was rejected: `CLAUDE.md` plans PL + EN, and under the old rule a second locale meant a
  second copy of every instruction — the same logic expressed twice, free to drift the first time
  either was tightened. Everything addressed to the model (both personas, the `<data>` field labels,
  the layer gloss) is now English, and names the answer's language through a single
  `OUTPUT_LANGUAGE` constant. The dividing line is *who the text is addressed to*: backend code and
  backend→AI text are English; anything a user reads stays localized. `ProposalTemplate` is
  therefore untouched and is now the backend's only locale-bound surface — its output *is* the
  sentence on the screen.
- **This reopened Phase 2 code**, so it lands in Phase 3's commit rather than as a follow-up: one
  class cannot hold two prompts following opposite rules without the split being a trap for whoever
  reads it next. `ProposalPromptTest` gained `tellsTheModelWhichLanguageToAnswerIn`, because losing
  that one line is the failure nothing else would catch — the request still assembles, the model
  still answers, and the user silently starts getting English.
- **`ProposalService.answer` gets one transaction, but not a `@Transactional` method.** A `STARTING`
  answer calls Sonnet, so a method-level annotation would pin a Hikari connection for the full
  60-second budget — the idleness anti-pattern `lessons.md` names for a metered, scale-to-zero Neon.
  The model is called first, outside any transaction; a `TransactionTemplate` then scopes the three
  writes (the entry's snooze or withdrawal, the proposal's answer, the memory episode) so they land
  together or not at all. The plan's Phase 2 note ("Phase 3's answer flow does and gets its own") was
  written before Phase 3 §3 put a model call inside that flow.
- **`AiMemoryService.record` creates the root when it is missing**, unlike `renderFor`, which
  renders blank. Both are invariant breaches (`RegistrationService` writes the row at t=0), but
  losing what the user just answered is the more expensive of the two failures.
- **The bullets are stored and returned, but not re-readable after a reload.** Phase 3's contract is
  the answer POST alone, and an answered proposal is no longer the pending one `POST /api/proposals`
  returns — so there is no path back to a proposal once answered. The plan's §3 intent ("a reload
  shows the same plan") needs a `GET /api/proposals/{id}` that the contract does not specify;
  flagged for Phase 4 or S-05 rather than added here.

### Adaptations taken during Phase 4 (2026-08-26)

- **The "nothing matched" message now triggers on `visible.length === 0 && goals.length > 0`**, not
  on "a filter is set". The withdrawn filter is the first one that is *on by default*, so the old
  condition would have shown a user whose only entry is withdrawn the empty screen of a brand-new
  account — the exact lie the message was added to prevent, and the one `load`'s failure banner
  refuses a few lines above it. The new condition is also shorter and subsumes the old one.
- **`replace(id, draft, completed, withdrawn)` became `replace(id, draft, {completed, withdrawn})`.**
  Two adjacent booleans in a positional call are a swap waiting to happen the day a third arrives,
  and both are primitives server-side — a swapped pair is a silent wrong write, not a 400.
- **A withdrawn row offers restore and delete, nothing else.** `Przywróć` is already the completion
  toggle's own label, so a withdrawn *and* completed entry would otherwise show two identically named
  buttons meaning different things. Completing or editing an entry the user has just said they will
  never act on is also not an action worth offering; they restore it first.
- **The card does not render `neglected_days`.** The plan's screen contract says the card names "how
  long it has been sitting", and it does — in the message, which cites it in prose. Rendering the
  number beside it would mean a second Polish plural rule in TypeScript, duplicating the one
  `ProposalTemplate` owns on the backend, for a fact the user has already read.
- **The spec's versioning comment was corrected rather than the version bumped.**
  `GoalUpdate.withdrawn` is a required primitive, so it is a genuinely *incompatible* change and
  1.4.0's old comment ("each was a compatible addition") would have been false. #114's answer is
  media-type versioning, deliberately not built: `x-audience` is component-internal, the only client
  is the SPA in this repository, and it gained the field in the same commit. The comment now says so,
  and says the exemption expires the day a second consumer exists.
- **`GoalApiTest.withdrawsAndRestoresAnEntry` pins the omitted-primitive 400.** That behaviour is the
  entire reason `withdrawn` waited three phases for this one, and nothing guarded it — boxing the
  field later would turn a loud 400 into a silent "not withdrawn".
- **The saved bullets carry no link back to the entry that produced them**, and that is FR-014 as
  written — it asks for "zadanie bieżące (FR-003)", a plain task. Noticed by the author during
  manual testing and parked as a post-submission slice candidate rather than folded in; the analysis
  and the return trigger live in `roadmap.md` → Parked, which outlives this folder.
- **Phase 3's flagged `GET /api/proposals/{id}` was not added.** The card holds the answered proposal
  in React state, so the bullets survive as long as the screen does; a reload simply starts over with
  the button. Still the right home for it is S-05, which needs to show a proposal the *scheduler*
  opened — at which point there is a proposal the client did not receive as a POST response.

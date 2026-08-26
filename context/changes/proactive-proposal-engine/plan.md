# S-04b — LLM-phrased proposal, four responses, first step

## Overview

S-04a shipped the deterministic half of the proactive loop: `ProposalSelector` picks the one entry
worth coming back to, and `POST /api/proposals` returns it as data. Nobody has ever seen it — there
is no UI, and the entry arrives with no prose around it.

This change closes the loop. The picked entry gets phrased by Sonnet (the first production call
`LlmClient` has ever received), the proposal is persisted so FR-018's at-most-one-pending rule holds,
the user answers it four ways, and "zaczynam" returns 3–5 saveable bullets.

## Current State Analysis

**What exists.**

- `proposal/ProposalSelector` — pure, no DB/clock/LLM, two rules (neglect, then category balancing).
  `ProposalSelectorTest` states the whole product rule without a container.
- `proposal/ProposalService` — reads the caller's entries via `GoalRepository`, runs the selector at
  `Europe/Warsaw`, maps to `ProposalResponse(entry, neglectedDays)`.
- `proposal/ProposalController` — `POST /api/proposals`, 200 or 204, no request body.
- `ai/LlmClient` — `complete` and `completeStructured(request, type, schema)`; the latter proven
  against strict `json_schema` on Sonnet by the gated `OpenRouterLiveTest`. **Zero production
  callers.** Slugs in `llm.model.haiku` / `llm.model.sonnet`.
- `ai/memory/AiMemory` + `AiMemoryRenderer` + `AiMemoryRepository.findByUserId` (join-fetches both
  child collections). `recordEpisode(eventType, payload, occurredAt)` exists on the aggregate.
- `goal/Goal` — three layers, `completed_at`, `due_date`, the time-fields invariant enforced three
  ways (aggregate, DTO, CHECK constraint).
- Frontend `pages/GoalsPage.tsx` (513 lines) — filters in the query string, send-then-refetch
  `save()`, `messageFor(status)` error copy, `api()` client with CSRF echo.

**What is missing, and it is load-bearing:** *nothing in the codebase ever creates an `AiMemory`
row.* S-03 was going to be its first writer and is cut from the submission window. Left alone, every
prompt would carry `_No memory recorded yet._` forever.

**Three guards that will fail loudly if this change is sloppy** — they are why several steps below
look like busywork and are not:

- `security/UserOwnedConventionTest` — ArchUnit: any `@Entity` mapping a `user_id` column **must**
  implement `UserOwned`. Structural, not a list; a new aggregate is covered automatically.
- `account/AccountDeletionIntegrationTest` — asserts against `information_schema` that every
  per-user table carries an FK to `app_user` with **no `ON DELETE` action**, so a missing
  `PerUserDataDeleter` makes account deletion fail loudly instead of silently retaining data.
- `ApiSurfaceTest` — `openapi.yaml` paths ≡ published controller paths, compared as a set **in both
  directions**. A new endpoint without a spec entry fails the build.

Migrations run to `V7`; the next is `V8`.

## Desired End State

On `/goals` there is a "Daj mi coś teraz" button. Pressing it returns a proposal card naming one
neglected entry in Polish prose that cites the entry and how long it has been sitting. The card
offers four answers. "Nie teraz" and "przypomnij za 7/30/90 dni" quiet the entry for a while;
"nigdy" moves it to the withdrawn filter, restorable; "zaczynam" returns 3–5 concrete bullets, each
saveable as a current task with one click. Pressing the button again while a proposal is unanswered
returns the same proposal, with no second model call.

Verified by: `/check` green, the gated live test extended and run once by hand with a real key, and a
manual pass through all four answers plus restore.

### Key Discoveries

- `ProposalSelector`'s javadoc (`proposal/ProposalSelector.java:57-66`) explicitly warns that "last
  interaction" is `goal.updated_at`, and that S-04b must **not** stamp that row with bookkeeping the
  user did not perform — or must move the clock to a `last_interaction_at` column. Snoozing and
  withdrawing *are* user-performed, so they belong on the `goal` row; anything the machine writes on
  its own (the message, the shown-at time, the generated bullets) belongs on the `proposal` row.
- `ProposalResponse`'s javadoc already anticipates this slice: *"`neglected_days` is not decoration —
  it is the engine's reason, and S-04b needs it to phrase one"*.
- `GoalUpdate` is deliberately **full-replace**: `completed` is a plain `boolean` where an omitted
  field means "active", not "leave alone". A new `withdrawn` flag must follow that same rule, and the
  frontend already threads `completed` through `replace()` explicitly — `withdrawn` rides along.
- `ai_memory_episode.payload` stores a raw JSON `String` mapped with `@JdbcTypeCode(SqlTypes.JSON)`,
  keeping the mapping free of Jackson coupling. The stored first step follows that precedent.
- `spring.ai.openai.timeout=60s`, `max-retries=1`. Left as-is by decision; the frontend carries the
  wait with a pending state.

## What We're NOT Doing

- **No scheduler, no email.** FR-011/FR-018's automatic cycle is S-05. This slice only ever produces
  a proposal because the user pressed a button.
- **No onboarding seed.** S-03's profile-fact seeding stays cut; memory fills from proposal answers
  only.
- **No priority categories.** FR-016 is S-06; balancing stays as S-04a shipped it.
- **No superseding.** The PRD's "unanswered = implicit *nie teraz*" applies when a cycle replaces a
  proposal — there are no cycles yet, so an unanswered proposal simply stays pending.
- **No withdrawn route.** Withdrawal is a third value in the existing S-08 filter row.
- **No `/api/goals` filter contract.** S-08 decided filtering happens in the browser; the withdrawn
  filter follows it rather than reopening that argument.

## Implementation Approach

Bottom-up, so each phase is verifiable on its own and the LLM enters as late as possible. Phase 1 is
schema and pure logic — no model, no HTTP. Phase 2 introduces the model behind a fallback, so the
endpoint answers even with the network unplugged. Phase 3 adds the answer semantics on top of a
proposal that already persists. Phase 4 is the screen. Phase 5 is the documentation walk that
`CLAUDE.md` counts as part of the slice.

The template fallback is built in Phase 2, not held in reserve for the 08.09 gate: it is the
`LlmException` catch arm either way, and building it now makes the entire loop testable without a
live model.

## Critical Implementation Details

**FR-018 as a schema invariant, not a service check.** At-most-one-pending is enforced by a partial
unique index on `proposal (user_id) WHERE answered_at IS NULL`. A service-level check would race with
itself on a double-click; the index cannot.

**The cascade weakens one guard, knowingly.** `proposal.goal_id` carries `ON DELETE CASCADE` so
`DELETE /api/goals/{id}` keeps working. Since every proposal has a goal and `GoalDataDeleter` runs
during account deletion, the cascade erases a user's proposals before `ProposalDataDeleter` is
reached — so the "missing deleter fails loudly" property does not actually protect this table. The
deleter is still written (it is three lines and becomes correct the moment a proposal can outlive its
goal), but nobody should trust the FK guard to catch its absence here.

**Snooze boundary.** `remind_after` is a `DATE` compared against the user's local date, matching how
`ProposalService` already reads the clock at `Europe/Warsaw` for `due_date`. An entry is skipped
while `today.isBefore(remindAfter)` — on the day itself it is eligible again.

---

## Phase 1: Schema and aggregate

### Overview

Everything the loop needs to persist, plus the selector's new eligibility rules. No LLM, no new
endpoint — the phase ends with `POST /api/proposals` behaving exactly as before while the schema
underneath it is ready.

### Changes Required:

#### 1. Migration

**File**: `backend/src/main/resources/db/migration/V8__create_proposal.sql`

**Intent**: Create the `proposal` table and add the two user-performed state columns to `goal`.
Expand-only and safe under an image rollback: both new `goal` columns are nullable, so the previous
image still inserts, and the new table is simply unused by it.

**Contract**: `proposal (id uuid pk, user_id uuid not null → app_user(id), goal_id uuid not null →
goal(id) on delete cascade, message text not null, neglected_days integer not null, source
varchar(16) not null, answer varchar(16), answered_at timestamptz, first_step jsonb, created_at
timestamptz not null, updated_at timestamptz not null)`. Index `user_id`. FR-018 as a partial unique
index:

```sql
CREATE UNIQUE INDEX idx_proposal_one_pending ON proposal (user_id) WHERE answered_at IS NULL;
```

Plus `ALTER TABLE goal ADD COLUMN remind_after DATE` and `ADD COLUMN withdrawn_at TIMESTAMPTZ`.
Follow `V6`/`V7`'s commenting register — the *why*, not the *what*.

#### 2. The aggregate

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/Proposal.java`

**Intent**: The proposal a user was shown and what they did about it. Implements `UserOwned` (the
ArchUnit rule requires it), UUIDv7 PK, `@CreationTimestamp` / `@UpdateTimestamp` audit columns per
the project persistence rules. Answering is an intent-revealing method, not a setter, so `answer` and
`answered_at` can never disagree.

**Contract**: `Proposal(UUID userId, UUID goalId, String message, long neglectedDays, Source source)`;
`void answer(ProposalAnswer answer, OffsetDateTime at)`; `void recordFirstStep(String stepsJson)`;
`boolean isPending()`. `first_step` maps via `@JdbcTypeCode(SqlTypes.JSON)` on a `String`, following
`ai_memory_episode.payload`. Answering an already-answered proposal throws.

#### 3. Answer and source enums

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalAnswer.java`,
`.../proposal/Proposal.Source` (nested)

**Intent**: The four FR-013 responses as a wire-contract enum, and a record of which arm produced the
message so a demo can tell a real Sonnet proposal from the fallback.

**Contract**: `ProposalAnswer { STARTING, NOT_NOW, REMIND_LATER, NEVER }`, SCREAMING_CASE on the wire
like `GoalLayer`. `Source { LLM, TEMPLATE }`.

#### 4. Repository and deleter

**File**: `.../proposal/ProposalRepository.java`, `.../proposal/ProposalDataDeleter.java`

**Intent**: Scoped access plus FR-019 erasure. Mirrors `GoalRepository` / `GoalDataDeleter` exactly.

**Contract**: `Optional<Proposal> findByUserIdAndAnsweredAtIsNull(UUID userId)`,
`Optional<Proposal> findByIdAndUserId(UUID id, UUID userId)`, `void deleteByUserId(UUID userId)`.
`ProposalDataDeleter implements PerUserDataDeleter`.

#### 5. Selector eligibility

**File**: `.../proposal/ProposalSelector.java`

**Intent**: A withdrawn entry is never proposed; a snoozed one is not proposed before its date. Both
are user-performed states, so they extend `isNeglected` rather than the comparator — they decide
eligibility, not ranking.

**Contract**: `Candidate` gains `LocalDate remindAfter` and `boolean withdrawn`, both read in
`Candidate.of(Goal)`. `isNeglected` returns false when `withdrawn`, or when
`today.isBefore(remindAfter)`. The silence map is **unchanged** — a withdrawn entry was touched when
it was withdrawn, so it still quiets its domain, which is the self-correcting behaviour balancing
already relies on.

#### 6. Wire the two new goal fields

**File**: `.../goal/Goal.java`, `.../goal/GoalResponse.java`, `.../goal/GoalUpdate.java`,
`.../goal/GoalService.java`

**Intent**: `Goal` learns `remindAfter` / `withdrawnAt` with intent-revealing mutators; the
representation publishes both (the frontend needs them for the withdrawn filter); `GoalUpdate` gains
a `withdrawn` boolean under the same full-replace rule `completed` already follows, which is what
makes restore a plain `PUT` rather than a new endpoint.

**Contract**: `Goal#snoozeUntil(LocalDate)`, `Goal#withdraw(OffsetDateTime)`, `Goal#restore()`.
`GoalResponse` gains `remindAfter`, `withdrawnAt` (→ `remind_after`, `withdrawn_at`).
`GoalUpdate` gains `boolean withdrawn`; `GoalService.update` applies it the way it applies
`completed`.

### Success Criteria:

#### Automated Verification:

- Migration applies cleanly against a fresh container: `mvn test -Dtest=ApplicationTests`
- Hibernate validates the new mappings (`ddl-auto=validate` — a mapping/schema mismatch fails boot)
- Selector rules hold: `mvn test -Dtest=ProposalSelectorTest` — new cases for withdrawn, for snoozed,
  and for the boundary day where `remind_after` equals today
- Aggregate rules hold: new `ProposalTest` — answering twice throws, `isPending` tracks `answered_at`
- Persistence round-trip: new `ProposalPersistenceTest` — the partial unique index rejects a second
  pending proposal for one user, and permits one per user across two users
- FR-019 still erases everything: `mvn test -Dtest=AccountDeletionIntegrationTest`
- Isolation convention: `mvn test -Dtest=UserOwnedConventionTest`
- Full gate: `/check`

#### Manual Verification:

- `DELETE /api/goals/{id}` on an entry with a pending proposal succeeds (the cascade works)

---

## Phase 2: LLM phrasing, with a fallback

### Overview

`LlmClient`'s first production call. The endpoint starts persisting the proposal and returning
Polish prose; it answers even when the model does not.

### Changes Required:

#### 1. Prompt assembly

**File**: `.../proposal/ProposalPrompt.java`

**Intent**: Build the Sonnet conversation from the memory block, the entry, and the neglect reason.
Pure function over its inputs — a string in, a string out — so its output can be asserted verbatim in
a unit test without a model or a container.

**Contract**: `static LlmRequest forProposal(String model, String memoryBlock, Goal entry, long
neglectedDays)`. System message states the persona (a friend who noticed, not a coach), the language
(Polish), the length ceiling (2–3 sentences), and the hard rule that it must cite the entry's own
words rather than inventing a new goal. User message carries the rendered memory block, the entry
text, its layer, its category and the idle time.

#### 2. The fallback

**File**: `.../proposal/ProposalTemplate.java`

**Intent**: The 08.09 gate's answer, built now. Turns the same inputs into the flat sentence the
roadmap names ("W styczniu wpisałeś *X* — minęło 8 miesięcy"), so the endpoint always returns a
proposal.

**Contract**: `static String phrase(Goal entry, long neglectedDays)`. Polish, no model, deterministic
— which is what lets `ProposalServiceTest` assert the fallback arm exactly.

#### 3. Memory, lazily created

**File**: `.../ai/memory/AiMemoryService.java` (new)

**Intent**: `AiMemory` has no writer anywhere in the codebase; without one, every prompt reads
"_No memory recorded yet._" forever. Give the aggregate a get-or-create seam owned by its own
package, so the proposal package never touches `AiMemoryRepository` directly.

**Contract**: `String renderFor(UUID userId)` (empty block when nothing exists yet, no row written)
and `void record(UUID userId, String eventType, String payload)` (creates the row on first write).

#### 4. Service and controller

**File**: `.../proposal/ProposalService.java`, `.../proposal/ProposalController.java`,
`.../proposal/ProposalResponse.java`

**Intent**: `propose()` returns the pending proposal untouched when one exists — that is what
at-most-one means under a manual trigger, and it stops button-mashing from burning Sonnet calls.
Otherwise it selects, phrases (falling back on `LlmException`), persists, and returns. The
representation gains the proposal's own id, its message and its source.

**Contract**: `ProposalResponse(UUID id, GoalResponse entry, long neglectedDays, String message,
Source source, ProposalAnswer answer, OffsetDateTime answeredAt, List<String> firstStep)` — the last
three null until Phase 3 fills them. `POST /api/proposals` still 200 / 204. The service is no longer
`@Transactional(readOnly = true)`.

#### 5. Spec

**File**: `context/foundation/openapi.yaml`

**Intent**: `ApiSurfaceTest` compares paths, but the schema is the part a reader trusts. Describe the
new fields and the at-most-one-pending behaviour of a repeated POST.

**Contract**: `Proposal` schema gains `id`, `message`, `source`, `answer`, `answered_at`,
`first_step`; `Goal` schema gains `remind_after` and `withdrawn_at`; `GoalUpdate` gains `withdrawn`.

### Success Criteria:

#### Automated Verification:

- Prompt is stable: new `ProposalPromptTest` — asserts the entry text, the idle days and the memory
  block all reach the request, and that the model slug is the Sonnet one
- Fallback is not dead code: new `ProposalServiceTest` drives a `LlmClient` double that throws
  `LlmException` and asserts a `TEMPLATE`-sourced proposal comes back with 200
- Second click is free: `ProposalApiTest` — two POSTs return the same proposal id, and the
  `LlmClient` double records exactly one call
- Spec parity: `mvn test -Dtest=ApiSurfaceTest`
- Full gate: `/check`

#### Manual Verification:

- `OPENROUTER_API_KEY=… mvn test -Dtest=OpenRouterLiveTest` — extended with one real proposal-prompt
  round-trip; read the Polish output and judge whether it sounds like a friend or like a coach
- The prose cites the entry rather than inventing a different goal

---

## Phase 3: Answers and the first step

### Overview

The four FR-013 responses, their effects, and FR-014's bullets.

### Changes Required:

#### 1. Answer endpoint

**File**: `.../proposal/ProposalController.java`, `.../proposal/ProposalAnswerRequest.java`

**Intent**: One endpoint for all four answers, returning the same representation the proposal already
has — now carrying the answer and, for STARTING, the stored bullets. One shape, so the client has one
thing to render.

**Contract**: `POST /api/proposals/{id}/answer`, body `{answer, remind_in_days?}` → 200
`ProposalResponse`. 404 when not found or not owned (never 403 — an id the caller does not own must
not be distinguishable from one that does not exist), 409 when already answered, 422 when
`REMIND_LATER` arrives without a 7/30/90 preset. Bean Validation on the request record.

#### 2. Answer effects

**File**: `.../proposal/ProposalService.java`

**Intent**: Apply each answer to the entry, and record it in memory so the next proposal knows what
happened. This is what makes FR-013's "każda odpowiedź wpływa na przyszłe propozycje" true rather
than aspirational.

**Contract**: `STARTING` → `snoozeUntil(today + 7)`; `NOT_NOW` → `snoozeUntil(today + 3)`;
`REMIND_LATER` → `snoozeUntil(today + n)` for n ∈ {7, 30, 90}; `NEVER` → `withdraw(now)`. Every
answer closes the proposal and writes an episode via `AiMemoryService.record`.

#### 3. First step

**File**: `.../proposal/ProposalPrompt.java`, `.../proposal/FirstStep.java`

**Intent**: On STARTING, ask Sonnet for 3–5 concrete bullets from its own knowledge (no web access in
MVP, per PRD Guardrails) and store them on the proposal, so a reload shows the same plan instead of
paying for a fresh call and quietly changing it.

**Contract**: `static LlmRequest forFirstStep(...)` plus a `JsonSchema` constraining
`{steps: string[]}` with 3–5 items, called through `completeStructured`. Serialized to the
`first_step` jsonb column. On `LlmException` the answer still lands — the proposal is answered and the
bullets come back empty, with the client saying so.

### Success Criteria:

#### Automated Verification:

- Each answer's effect: new cases in `ProposalApiTest` — after `NEVER` the entry carries
  `withdrawn_at` and no longer wins a proposal; after `REMIND_LATER 30` it is skipped for 30 days
- Guard rails: 409 on a second answer, 422 on `REMIND_LATER` without a valid preset, 404 for another
  user's proposal
- First step stored: STARTING returns 3–5 bullets and a re-read returns the same ones with no second
  model call
- Degraded path: a throwing `LlmClient` double still answers STARTING with 200 and empty bullets
- Full gate: `/check`

#### Manual Verification:

- Live key, `POST` a real STARTING and read the bullets — are they concrete enough to act on?

---

## Phase 4: The screen

### Overview

The card on `/goals`, the withdrawn filter, restore.

### Changes Required:

#### 1. Proposal card

**File**: `frontend/src/pages/ProposalCard.tsx` (new), `frontend/src/pages/GoalsPage.tsx`

**Intent**: The button, the pending state, the four answers, the bullets. A separate file because
`GoalsPage` is already 513 lines and this is a self-contained flow — it takes the page's `save`-style
refetch as a prop rather than owning the entry list.

**Contract**: Button "Daj mi coś teraz" → `POST /api/proposals`; 204 renders "nic nie czeka" rather
than an empty card. Pending state disables the button and shows a spinner (the wait budget is the
client's 60s, by decision). Four answer buttons; `REMIND_LATER` reveals the 7/30/90 choice. STARTING
renders the bullets, each with "zapisz jako zadanie" → `POST /goals` with `layer: TASK`. Every answer
refetches the entry list, because all four change it.

#### 2. Withdrawn filter and restore

**File**: `frontend/src/pages/GoalsPage.tsx`

**Intent**: Withdrawn entries are hidden by default and reachable through the existing filter row,
with restore reusing the `PUT` the edit form already uses.

**Contract**: A third control in `Filters` writing `?withdrawn=1`; the `visible` predicate excludes
`withdrawn_at` entries unless it is set. A "przywróć" button per withdrawn entry calling `replace`
with `withdrawn: false`. `Goal` type gains `remind_after` and `withdrawn_at`; `replace` threads
`withdrawn` the way it already threads `completed`.

### Success Criteria:

#### Automated Verification:

- `npx vitest run src/pages/ProposalCard.test.tsx` — 204 renders the empty message; each of the four
  answers posts the right body; STARTING renders bullets and each saves a task; a failed POST shows
  the error banner rather than an empty card
- `GoalsPage.test.tsx` — withdrawn entries are hidden by default, shown under the filter, and restore
  sends `withdrawn: false`
- `npm run lint`, `npm run build`
- Full gate: `/check`

#### Manual Verification:

- Click through the whole loop against a live backend with a real key: propose → each of the four
  answers in turn → restore a withdrawn entry
- The spinner is visible and the button cannot be double-fired
- Polish copy reads naturally beside the existing screens

---

## Phase 5: Documentation walk

### Overview

`CLAUDE.md` counts the docs as part of the slice, and names the exact list where drift has landed
before. Walk all nine.

### Changes Required:

#### 1. The architecture page

**File**: `docs/index.html`

**Intent**: Describe what merged, on every surface that describes capability — not just the ones that
are easy to find.

**Contract**: `#overview` capability badge for the proposal loop; `#code-map` row for the grown
`proposal` package; `#backend` prose **and class diagram** for `proposal` (new classes, the answer
enum, the selector's new eligibility inputs) and for `ai/memory` (its first writer); `#backend` terms
`<dl>` for the new nouns (proposal, wycofanie, pierwszy krok); `#flows` for the implemented loop, and
any planned flow this contradicts; `#data` prose for the new table and columns, with the ER SVGs
re-exported since the schema changed; `#roadmap` cards and `#glossary` rows; the endpoint list in the
`#backend` note; and the `Verified against …` stamp with DEV-23.

#### 2. Roadmap

**File**: `context/foundation/roadmap.md`

**Intent**: S-04 is complete once S-04b lands — both halves shipped.

**Contract**: Slice-table row S-04 → `done`; the per-slice `### S-04` section's `**Status:**`; the
deadline-plan row for 09-03 – 09-08; the "buildable now" table's S-04 row, and S-05's note, which
currently says it waits on S-04.

### Success Criteria:

#### Automated Verification:

- `node --test docs/index.test.mjs`
- ER SVGs re-exported and the light-palette fix applied (the draw.io CLI always writes
  `color-scheme: light dark`, which the docs test rejects)
- Full gate: `/check`

#### Manual Verification:

- Open `docs/index.html` in a browser — Mermaid cannot render headlessly here, so the class-diagram
  edits are unverified until someone looks
- Read `#flows` and `#overview` as a stranger: do they describe the app that now exists?

---

## Testing Strategy

### Unit Tests

- `ProposalSelectorTest` — withdrawn excluded, snoozed excluded, boundary day eligible, silence map
  still counts withdrawn entries
- `ProposalTest` — answer-once invariant, `isPending`
- `ProposalPromptTest` — the entry, the idle days and the memory block all reach the request
- `ProposalServiceTest` — the fallback arm, with a throwing `LlmClient` double
- `ProposalCard.test.tsx` — the four answers, the bullets, the failure copy

### Integration Tests

- `ProposalPersistenceTest` — the partial unique index enforces one pending proposal per user
- `ProposalApiTest` (REST Assured, per project convention) — the whole endpoint contract including
  409 / 422 / 404 and the second-click-is-free rule
- `AccountDeletionIntegrationTest` — unchanged, must stay green
- `ApiSurfaceTest` — spec parity

### Manual Testing Steps

1. `OPENROUTER_API_KEY=… mvn test -Dtest=OpenRouterLiveTest` and read the Polish output.
2. Run both halves locally; press the button with entries neglected in several domains.
3. Answer "nie teraz", press again — a different entry, or 204.
4. Answer "nigdy", switch the withdrawn filter on, restore the entry.
5. Answer "zaczynam", save two bullets as tasks, confirm they appear in the task layer.
6. Kill the network mid-flight and press the button — a template proposal, not an error.

## Performance Considerations

One Sonnet call per new proposal and one per STARTING; the second-click rule means button-mashing
costs nothing. `ProposalService` still reads the caller's whole entry list — unchanged from S-04a and
still the right trade at single-user scale, with `GET /api/goals`'s documented ~500-entry pagination
trigger as the shared upgrade path.

## Migration Notes

`V8` is expand-only: both `goal` columns are nullable and the new table is inert to the previous
image, so an image rollback is safe. No backfill — existing entries have no snooze and no withdrawal,
which is exactly the intended default.

## References

- Decisions taken before planning: `context/changes/proactive-proposal-engine/change.md`
- The deterministic half this builds on: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalSelector.java`
- The warning this plan answers: `ProposalSelector.java:57-66`
- Prior slice of comparable shape: `context/changes/goals-and-dreams/plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Schema and aggregate

#### Automated

- [x] 1.1 Migration applies cleanly against a fresh container — 2d8ada1
- [x] 1.2 Hibernate validates the new mappings — 2d8ada1
- [x] 1.3 ProposalSelectorTest — withdrawn, snoozed, boundary day — 2d8ada1
- [x] 1.4 ProposalTest — answer-once invariant — 2d8ada1
- [x] 1.5 ProposalPersistenceTest — partial unique index — 2d8ada1
- [x] 1.6 AccountDeletionIntegrationTest green — 2d8ada1
- [x] 1.7 UserOwnedConventionTest green — 2d8ada1
- [x] 1.8 /check green — 2d8ada1

#### Manual

- [x] 1.9 DELETE a goal carrying a pending proposal — 2d8ada1

### Phase 2: LLM phrasing, with a fallback

#### Automated

- [x] 2.1 ProposalPromptTest — inputs reach the request — 434b071
- [x] 2.2 ProposalServiceTest — template fallback on LlmException — 434b071
- [x] 2.3 ProposalApiTest — second click returns the same proposal, one model call — 434b071
- [x] 2.4 ApiSurfaceTest green — 434b071
- [x] 2.5 /check green — 434b071

#### Manual

- [x] 2.6 OpenRouterLiveTest with a real key — read the Polish output — 434b071
- [x] 2.7 The prose cites the entry rather than inventing one — 434b071

### Phase 3: Answers and the first step

#### Automated

- [x] 3.1 Each answer's effect on the entry — fa7ef0d
- [x] 3.2 409 / 422 / 404 guard rails — fa7ef0d
- [x] 3.3 First step stored and re-read without a second call — fa7ef0d
- [x] 3.4 Degraded STARTING still answers 200 — fa7ef0d
- [x] 3.5 /check green — fa7ef0d

#### Manual

- [x] 3.6 Live STARTING — are the bullets concrete enough to act on? — fa7ef0d

### Phase 4: The screen

#### Automated

- [x] 4.1 ProposalCard.test.tsx — answers, bullets, failure copy — 1d612f7
- [x] 4.2 GoalsPage.test.tsx — withdrawn filter and restore — 1d612f7
- [x] 4.3 npm run lint + npm run build — 1d612f7
- [x] 4.4 /check green — 1d612f7

#### Manual

- [x] 4.5 Full loop against a live backend — 1d612f7
- [x] 4.6 Spinner visible, button cannot double-fire — 1d612f7
- [x] 4.7 Polish copy reads naturally — 1d612f7

### Phase 5: Documentation walk

#### Automated

- [x] 5.1 node --test docs/index.test.mjs — 3908964
- [x] 5.2 ER SVGs re-exported with the light-palette fix — 3908964
- [x] 5.3 /check green — 3908964

#### Manual

- [x] 5.4 Open docs/index.html — Mermaid diagrams render — 3908964
- [x] 5.5 #flows and #overview describe the app that now exists — 3908964

# S-04b — LLM-phrased proposal, four responses, first step — Plan Brief

> Full plan: `context/changes/proactive-proposal-engine/plan.md`
> Decisions taken before planning: `context/changes/proactive-proposal-engine/change.md`

## What & Why

S-04a shipped the deterministic half of the proactive loop — `ProposalSelector` picks the one
neglected entry worth returning to — but nobody has ever seen it: there is no UI, and the entry
arrives as data with no prose around it. This change closes the loop and puts the product's core
value claim in front of a user: an AI that notices what you stopped doing and asks about it by name.

It is also where `LlmClient` receives its first production call. Until now the OpenRouter path has
only ever run under a gated live test.

## Starting Point

`POST /api/proposals` returns `{entry, neglected_days}` and 204 when nothing is neglected.
`AiMemoryRenderer` can render a memory block, but **nothing in the codebase ever creates an
`AiMemory` row** — S-03 was its first writer and is cut from the submission window. The frontend has
no proposal UI at all. Three guards constrain any new aggregate: an ArchUnit isolation rule, an
`information_schema` assertion on FR-019 erasure, and a both-directions parity test between
`openapi.yaml` and the published controller paths.

## Desired End State

`/goals` carries a "Daj mi coś teraz" button. Pressing it returns a card naming one neglected entry
in Polish prose that cites the entry and how long it has sat. Four answers: "zaczynam" returns 3–5
concrete bullets, each saveable as a current task with one click; "nie teraz" and "przypomnij za
7/30/90 dni" quiet the entry; "nigdy" moves it to the withdrawn filter, restorable. Pressing the
button again while a proposal is unanswered returns the same proposal, with no second model call.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Proposal persistence | A `proposal` table now | Running nine days ahead, so FR-018's at-most-one-pending is enforced today rather than retrofitted by S-05 | change.md |
| At-most-one-pending | Partial unique index on `(user_id) WHERE answered_at IS NULL` | A service check races with a double-click; an index cannot | Plan |
| Repeated button press | Returns the pending proposal, no second model call | That *is* at-most-one under a manual trigger, and it stops mashing from burning Sonnet calls | change.md |
| Snooze mechanism | `goal.remind_after` date, written by three of the four answers | One mechanism, different defaults — not two names for one thing | change.md |
| "Zaczynam" effect | Snooze a week | Otherwise the next click re-proposes the same entry — the hole that breaks a demo | Plan |
| Memory grounding | This slice writes episodes and lazily creates the `AiMemory` row | Without a writer the memory block reads "_No memory recorded yet._" forever, and "osadzona w pamięci AI" is vacuous | Plan |
| Withdrawn UI | Third value in the S-08 filter row, not a route | Withdrawal is another answer to "which entries am I looking at", which that row already owns | change.md |
| Restore | Reuses `PUT /api/goals/{id}` with `withdrawn: false` | `GoalUpdate` is already full-replace and the frontend already threads `completed` the same way | Plan |
| Wait budget | Keep the 60s client timeout, show a spinner | Author's call: never discard a good answer. Consequence — the fallback needs a unit test so it is not dead code | Plan |
| Template fallback | Built now, not held for the 08.09 gate | It is the `LlmException` catch arm either way, and it makes the whole loop testable without a live model | change.md |
| First step | Stored on the proposal row | A reload shows the same plan instead of paying for a fresh call and silently changing it | Plan |
| Save a bullet | One button per bullet | Matches FR-014's "wybrany punkt" and reuses `POST /goals` unchanged | Plan |

## Scope

**In scope:** the `proposal` aggregate and migration; `remind_after` / `withdrawn_at` on `goal`;
selector eligibility for both; Sonnet phrasing with a template fallback; lazy `AiMemory` creation and
episode writing; the answer endpoint and the four effects; the stored first step; the proposal card,
the withdrawn filter and restore; the full documentation walk.

**Out of scope:** the scheduler and email delivery (S-05); onboarding seed (S-03); priority
categories (S-06); superseding an unanswered proposal; a withdrawn route; any `/api/goals` filter
contract.

## Architecture / Approach

`ProposalService` stays the one use case. It now checks for a pending proposal first, and only on a
miss does it select, phrase, persist and return. Phrasing goes through a pure `ProposalPrompt` into
`LlmClient`, with `ProposalTemplate` as the `LlmException` arm — so the endpoint answers with the
network unplugged. Answers land on a second endpoint that applies the effect to the `goal` row,
closes the proposal, and records an episode. The selector stays pure: it learns two new eligibility
inputs and nothing else.

The split of state is deliberate and follows a warning `ProposalSelector`'s own javadoc left for this
slice: user-performed state (snooze, withdrawal) goes on the `goal` row where "last interaction"
legitimately means the user; machine-written state (the message, the generated bullets) goes on the
`proposal` row so it can never reset the neglect clock.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Schema and aggregate | `V8`, the `Proposal` aggregate, selector eligibility | The cascade on `goal_id` weakens the missing-deleter guard — documented, not silently accepted |
| 2. LLM phrasing | First production model call, with a fallback | Prompt quality is the product's core value claim and cannot be unit-tested |
| 3. Answers and first step | The four responses and FR-014's bullets | Answer semantics must not reset the neglect clock by accident |
| 4. The screen | The card, the withdrawn filter, restore | `GoalsPage` is already 513 lines |
| 5. Documentation walk | `docs/index.html`, spec, roadmap | A partial pass reads exactly like a complete one |

**Prerequisites:** none outstanding — F-02, S-01, S-02, S-07, S-08 and S-04a are all on `master`. A
real `OPENROUTER_API_KEY` is needed for the manual verification in phases 2 and 3.
**Estimated effort:** ~4 sessions across 5 phases (3 were budgeted before the scope extension).

## Open Risks & Assumptions

- **Prompt quality is the one thing no test can assert.** Phases 2 and 3 both end with a human
  reading the Polish output. If it sounds like a coach rather than a friend, that is a prompt
  iteration, not a bug — budget for it.
- **The fallback is now rarely exercised in practice** (60s timeout, by decision), so its unit test is
  the only thing keeping it honest until the gate needs it.
- **The first proposal any user sees has an empty memory block.** Memory only becomes interesting
  after a few answers; a demo should run a few interactions first.
- **A stored first step can go stale** if the entry is edited afterwards. Accepted — the alternative
  silently changes the plan under the user on every reload.

## Success Criteria (Summary)

- Pressing "daj mi coś teraz" returns a proposal that names a real neglected entry in natural Polish,
  and pressing it again returns the same one rather than a second model call.
- All four answers change what the engine proposes next, and "nigdy" is reversible.
- "Zaczynam" produces 3–5 bullets concrete enough that saving one as a task is worth doing.

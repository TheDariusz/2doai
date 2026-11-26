# Frame Brief: Task time-of-day and due-date reminder (DEV-63)

> Framing step before /10x-plan. This document captures what is *actually*
> at issue, separated from what was initially assumed.

## Reported Observation

A task carries only a date (`due_date`), no time of day. The app sends nothing on the due day or
before it; the only outbound channel is the natural rhythm (a random return every 2–7 days at a
random hour), and a due date only nudges which entry gets picked. A doctor's visit at 12:00 cannot
be entered with its time, and no reminder arrives that day or ahead of it — a dated task cannot be
relied on.

## Initial Framing (preserved)

- **User's stated cause or approach**: Two missing pieces — the task has no time field, and nothing
  fires at a fixed moment. A reminder must arrive independently of the rhythm, without taking a
  proposal slot, and must be scheduled in memory so Neon can still sleep.
- **User's proposed direction**: Nullable `due_time` column (TASK only, only together with a date),
  a sent-state marker (`reminder_sent_at`), an in-memory reminder map fed by `GoalService` events with
  the column as restart backup, a localized reminder e-mail (maybe Web Push), and a PRD note so the
  fixed-moment reminder does not read as contradicting the "moment nobody set" principle.
- **Pre-dispatch narrowing**: A real near-miss ("I forgot / nearly forgot"), not a vague feeling.
  Both shapes matter: an appointment at a specific date **and** time, and a plain deadline ("by
  Friday"). The channel that works today is **e-mail read on the phone**.

## Dimension Map

1. **Data model** — `due_date` is `DATE`; a time of day has nowhere to live.  ← initial framing (part 1)
2. **Trigger** — nothing fires because of a date; a date only biases the rhythm's selector.  ← initial framing (part 2)
3. **Pipeline fit** — a reminder might ride the existing proposal pipeline, or that pipeline's
   invariants (FR-018) might forbid it, making a separate mechanism necessary.
4. **Product principle** — a fixed-time reminder might contradict the PRD's natural-rhythm guardrail
   or the calendar Non-Goals, or may already have been decided/parked.

(Channel reliability was considered and dropped: the user reads proposal e-mails on the phone today.)

## Hypothesis Investigation

| Hypothesis | Evidence | Verdict |
| --- | --- | --- |
| 1. No time field | `V7__goal_due_date.sql:10` (`DATE`); `Goal.TIME_FIELDS_RULE` allows only `dueDate` on TASK | STRONG |
| 2. Nothing fires on a date | Only sender of proposal mail is `ProposalScheduler.fire` (`ProposalScheduler.java:222-230`), reacting to `ApplicationReadyEvent`/`UserVerified` only; rhythm is pure random (`ProposalRhythm.java:45-58`). **Bias is overdue-only**: `isOverdue = dueDate.isBefore(today)` (`ProposalSelector.java:159-160`) — a task due *today* gets no boost at all; snoozed entries are excluded even when overdue (`:138`) | STRONG |
| 3. Proposal pipeline can't carry it | One-pending index `idx_proposal_one_pending` (`V8__create_proposal.sql:57`); `proposeScheduled` supersedes the pending proposal → implicit "not now", 3-day snooze, `proposal_superseded` memory episode (`ProposalService.java:193, 219-236, 378-396`); every fire reschedules `next_proposal_at` (`ProposalScheduler.java:236, 249-253`). Proposal mail deliberately has no action links (`ProposalEmail.java:19-20`). Only goal event is `GoalLeftTheRunning` (complete/withdraw); no create/update/delete events (`GoalService.java:43, 83-85, 94`). Scheduler map is per-account `Map<UUID, OffsetDateTime>` (`ProposalScheduler.java:69`) — tick/window/health/delete hooks generalise, key/storage/reschedule do not | STRONG |
| 4. Principle conflict / prior decision | Rhythm guardrail (`prd.md:53`) scopes to neglected **goals/dreams**; Non-Goals (`prd.md:185, 190`) are about time-blocking and calendar sync; FR-011 note (`prd.md:120`) says due date feeds the rhythm. Nothing rules on due-date alerts; S-07 (DEV-27) recorded no decision. `Goal.java:30` / `roadmap.md:99` foresee "overdue alarms" as a future reason to split the aggregate. Only tonal friction: `docs/index.html` ~191, ~736, ~2997 ("not a fixed-time alarm", "the alarm clock this product exists not to be") | WEAK (adjacent, not contradictory) |

## Narrowing Signals

- The near-miss was on a commitment kept **in another tool**, which also failed to save it — so the
  job is to make 2doai a place worth trusting with time-bound commitments, and the reminder is what
  earns that trust. It is not a fix for an entry 2doai lost.
- For a date-only deadline, the useful moment is **the morning of the due day** — the issue's own
  default, confirmed, not a multi-day lead.
- E-mail on the phone is the channel the user already notices; Web Push is not needed to address the
  observation.
- Investigation sharpened dimension 2: today the app speaks about a dated task only **after** the date
  has passed (overdue bias), and then only if the random rhythm happens to fire.

## Cross-System Convention

Todo apps (Todoist, TickTick, Things) separate *the user's own deadline alerts* (user-set moment,
deterministic) from *engagement nudges*. That matches the reframe below: two mechanisms with
distinct owners of the moment. Inside this codebase the convention is set by `lessons.md` (scale-to-zero
DB) and `ProposalScheduler` — timers live in memory, the DB is touched only at boot, on change, and
on fire. The issue's direction already follows both.

## Reframed (or Confirmed) Problem Statement

> **The actual problem to plan around is**: the app has no notion of a **user-set moment** — a task
> can't hold a time of day, and nothing ever fires *because* of the user's date (the only date
> signal fires after the date has passed, at a random hour) — so a time-bound commitment can't be
> trusted to 2doai.

The initial framing was correct — proceed with the originally proposed direction. The investigation
adds two constraints the plan must honour rather than changing the direction: (a) the reminder must
be a **separate mechanism** from proposals, since sending through `proposeScheduled` would supersede
the pending proposal, write a false "not now" into AI memory and reset the rhythm; (b) the PRD/docs
need a stated line — *the AI owns the moment of a proposal; the user owns the moment of a reminder* —
so it doesn't read as contradicting "natural rhythm, not an alarm clock".

## Confidence

**HIGH** — strong code evidence on dimensions 1–3, no documented prior decision against it,
decisive narrowing (real near-miss; morning-of for deadlines; e-mail on phone already noticed).

## What Changes for /10x-plan

Plan the issue's direction as written, with these as givens rather than open questions: reminder is
not a `Proposal` and must not touch the pending proposal, rhythm or memory; morning-of-due-day for
date-only tasks; **date and time apply to `TASK` only** — a `GOAL` keeps its horizon and a `DREAM`
stays timeless, so neither may carry a due date, a due time, or a reminder (product-owner confirmed
2026-11-26; extends `Goal.TIME_FIELDS_RULE` and the `chk_goal_layer_time_fields` CHECK, with a time
allowed only alongside a date); e-mail only (Web Push stays post-MVP per FR-018 note). Still for the plan / product
owner: lead time for timed tasks, the morning hour, an action link in the mail, the new goal events
(`GoalService` publishes none for create/update/delete today), and the PRD + `docs/index.html`
wording (glossary "Natural rhythm", roadmap card, overview line ~191).

## References

- Source: `proposal/ProposalScheduler.java:69, 222-253`, `proposal/ProposalSelector.java:123-160`,
  `proposal/ProposalService.java:193-236, 378-396`, `proposal/ProposalRhythm.java:45-58`,
  `proposal/ProposalEmail.java:19-51`, `goal/GoalService.java:43-94`, `goal/Goal.java:30`,
  `db/migration/V7__goal_due_date.sql:10`, `db/migration/V8__create_proposal.sql:57`
- Docs: `context/foundation/prd.md:53, 88, 120, 149, 185, 190`; `roadmap.md:59, 99, 341-355`;
  `docs/index.html` ~191, ~725, ~736, ~2997; `context/foundation/lessons.md` (scale-to-zero DB)
- Linear: DEV-63
- Investigation tasks: delivery-path trace (dimensions 2–3), product-docs search (dimension 4)

# Task Time-of-Day and Due-Date Reminder (DEV-63) — Plan Brief

> Full plan: `context/changes/due-date-reminder/plan.md`
> Frame brief: `context/changes/due-date-reminder/frame.md`

## What & Why

> The app has no notion of a **user-set moment**. A task can't hold a time of day, and nothing ever
> fires *because* of the user's date: the only date signal fires after the date has passed, at a
> random hour. So a time-bound commitment can't be trusted to 2doai.

This plan gives a task an optional time and a reminder lead, and e-mails the user once, at the moment
they set.

## Starting Point

`goal.due_date` is a bare `DATE`. The only outbound mail is the natural-rhythm proposal, which
cannot carry a reminder without superseding the pending proposal, writing false memory and
resetting the rhythm. `ProposalScheduler` already shows how to keep timers in memory so Neon can
sleep.

## Desired End State

A task can say "12:00, remind me 1 h before" (or 15 min, at the time, or the morning of). A
date-only task is reminded at 08:00 on its due day. One localized e-mail arrives per task. It still
arrives late after downtime, as long as the due moment hasn't passed. Proposals, the rhythm and AI
memory are untouched.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Separate mechanism | New `reminder` context, not a `Proposal` | `proposeScheduled` would supersede, write "not now" and reset the rhythm | Frame |
| Layers | Date, time and reminder on `TASK` only | A goal keeps its horizon and a dream stays timeless | Frame |
| Channel | E-mail only | It's what the user already reads on the phone | Frame |
| Lead | Per-task picker, only when a time is set: `AT_TIME` / `FIFTEEN_MINUTES` / `ONE_HOUR` / `MORNING_OF`, default `ONE_HOUR` | Covers "leave now" through "plan the day" without a picker on date-only tasks | Plan |
| Morning hour | 08:00 Europe/Warsaw, configurable | Before the workday, and tunable without code | Plan |
| Missed moment | Send late while the due moment (+5 min grace) hasn't passed, then drop | Survives a deploy across 08:00 without stale reminders; the grace lets `AT_TIME` fire on a 60s tick | Plan |
| Batching | One mail per task | Simplest state (keyed by goal id), and the subject names the entry | Plan |
| Mail link | `/goals` only, no action links | A forwarded mail is never an unauthenticated write | Plan |
| Delivery | Stamp `reminder_sent_at`, commit, then send (at-most-once) | A retry loop would keep Neon awake while mail is down | Plan |
| Re-arm | A change of date/time/lead clears the sent marker | A moved appointment deserves a new reminder | Plan |

## Scope

**In scope:** the `V18` columns + CHECK; `Goal` invariant; `ReminderLead` enum; API contract
(spec + Java + TS); goal events; the `reminder` scheduler, fire transaction, PL/EN mail, liveness
indicator, account erasure; SPA time and lead controls + chip; PRD note; `data-model.md`; full
`docs/index.html` walk.

**Out of scope:** Web Push, action links, digests, a per-user hour or zone, `DAY_BEFORE` or `NONE`,
send retries, recurrence, a `Task` aggregate split, any proposal-pipeline change.

## Architecture / Approach

`GoalService` publishes value-only `GoalTermChanged` and `GoalDeleted` events inside its write
transaction. `ReminderScheduler` keeps `Map<goalId, (userId, moment, expiry, notBefore)>` in memory, with expiry = due moment + a 5-minute grace. It loads
the map with one query at boot, and its 60s tick does no I/O. A pure `ReminderMoment` turns a term
into its moment and expiry in the user zone. A fire locks the goal, re-checks it, stamps
`reminder_sent_at` and commits, then sends a plain-text localized e-mail.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Term model and contract | Time + lead stored, validated at 3 depths, spec/Java/TS aligned | `LocalTime` serializing as `HH:mm:ss` instead of `HH:mm` |
| 2. Reminder scheduler and mail | One e-mail per dated task at the user's moment | Accidentally querying from the tick, touching proposal state, or moving `goal.updated_at` (the rhythm's neglect clock) |
| 3. SPA form and chip | Time input, lead select, time on the chip | Form state (clearing date/time cascades) |
| 4. PRD and living docs | Ownership line stated, `docs/index.html` current | A partial docs walk that still passes the test |

**Prerequisites:** branch `thedariusz/dev-63-zadanie-na-konkretna-godzine-i`; dev Postgres up; Resend
credentials for the real-mailbox check.
**Estimated effort:** ~3–4 sessions across 4 phases.

## Open Risks & Assumptions

- At-most-once delivery: a provider failure loses that reminder (logged at ERROR, not retried).
- The single hard-coded zone (Europe/Warsaw) is assumed correct for every account.
- `outlook.com` recipients may silently drop mail from a young domain (`lessons.md`).
- `USER_ZONE` moves out of `proposal` to a shared home, so both schedulers decide "the user's day" in
  one place.

## Success Criteria (Summary)

- A timed task entered on the phone produces one e-mail, at the chosen lead before its time, in the
  account's language.
- A date-only task produces one e-mail at 08:00 on its due day, even if the app restarted across 08:00.
- The pending proposal, the rhythm's next moment and AI memory are unchanged by any reminder.

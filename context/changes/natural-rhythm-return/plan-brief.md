# S-05 — Natural Rhythm Return — Plan Brief

> Full plan: `context/changes/natural-rhythm-return/plan.md`

## What & Why

The gwiazda przewodnia: the AI returns to the user on its own, in a random ~2–7-day rhythm, with a
proposal for a neglected goal or dream — delivered by email with a link and visible in the app at
next open (FR-011, FR-018, US-01). This slice decides whether the product's core claim ("a caring
friend, not a scheduler") is real; everything after it is sequenced behind its validation.

## Starting Point

S-04 shipped the whole engine behind a manual button: `ProposalService` selects, phrases (Sonnet
with a template fallback), and persists under a DB-enforced at-most-one-pending rule. But nothing
fires it autonomously, a pending proposal is permanent until answered, the SPA only sees proposals
via POST (button), and the project has zero scheduling and zero email infrastructure. The Fly
machine is already pinned always-on for this scheduler, and lessons.md dictates the hard
constraint: nothing may touch Neon more often than its ~5-minute autosuspend window.

## Desired End State

At an unpredictable moment inside 9:00–21:00 Warsaw time, on average every 2–7 days, the user gets
a Polish email containing the proposal and a link to `https://2doai.app/goals`; opening the app
shows the same proposal in the existing card with the four answers. An ignored proposal is closed
as an implicit "nie teraz" when the next cycle replaces it. Restarts don't reset the rhythm, and
the app generates no database traffic between fires.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Rhythm model | Uniform random draw: 2–7 days ahead, random time inside the window | Implements the PRD guardrail literally as a pure function; answers already shape *what* via snooze, so adaptivity isn't needed |
| Timing state | `app_user.next_proposal_at` (V9), loaded to memory at boot, written only when firing | Restart-proof and Neon-safe — the write piggybacks on DB work already happening |
| Superseding | New machine-only `SUPERSEDED` answer value + NOT_NOW effect (+3d snooze) | Works with the existing CHECK and partial index unchanged, and keeps memory honest (ignored ≠ answered) |
| Supersede ordering | Select first (old goal excluded); supersede only when a replacement exists | FR-018's implicit "nie teraz" happens only on actual replacement, and the new proposal never re-names the ignored goal |
| Quiet hours / timezone | Fixed Europe/Warsaw, 9:00–21:00 (config record, not per-user) | Extends the existing documented seam; a `user.timezone` column has nothing to populate it yet |
| Email provider | Resend | Free tier covers MVP volume many times over; fastest solo-dev setup on Cloudflare DNS |
| Transport | `spring-boot-starter-mail` (SMTP) behind an `EmailSender` port | Zero new supply chain; switching providers stays a credentials change |
| Email content | The stored proposal message + `/goals` link, text-first | Exactly FR-018's "treść propozycji + link" — the email *is* the friend's message |
| In-app surfacing | New `GET /api/proposals/pending` + auto-load on `/goals` mount | Side-effect-free read — no accidental Sonnet call on page open |
| Send failure | Log and keep the proposal pending | In-app is the guaranteed channel; no retry infrastructure |

## Scope

**In scope:** V9 migration; `ProposalRhythm` pure function; `SUPERSEDED` closure + scheduled
propose path; first `@EnableScheduling` (in-memory schedule, DB-free tick, boot load, registration
event, liveness indicator); `mail` package over Resend SMTP; the proposal email; `GET
/api/proposals/pending` + card auto-load; full docs walk + runbook addendum.

**Out of scope:** per-user timezone/locale; tokenized answer-links; email retry queue;
notification preferences; adaptive rhythm; S-03/S-06; any change to the manual trigger.

## Architecture / Approach

Everything scheduler-shaped joins the existing `proposal` package (package-private seams stay
closed); only the `EmailSender` port gets a new `mail` package. One nullable timestamp per user is
the whole persistent rhythm state, mirrored in a `ConcurrentHashMap`; a 60-second in-memory tick
touches the DB only when a user is due inside the window — supersede-if-replacing → select →
phrase → persist → email → draw and persist the next fire time.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Schema & superseding | V9, rhythm function, SUPERSEDED, `proposeScheduled` | Supersede ordering subtly wrong → user left with nothing or re-nagged about the ignored goal |
| 2. Scheduler | First background job, boot load, tick, liveness | A stray DB touch on the tick path silently pins Neon awake |
| 3. Email | Resend SMTP behind a port, the proposal email | Deliverability/domain setup is outside the codebase (manual DNS) |
| 4. In-app surfacing | GET pending + card auto-load | Contract parity (openapi ↔ controller ↔ TS enum) |
| 5. Docs & deployment | Full docs walk, runbook, prod smoke | A partial docs pass reads exactly like a complete one |

**Prerequisites:** S-04 + F-02 on `master` (done). For phase 3's manual step: a Resend account and
DNS access to `2doai.app` on Cloudflare — user-side. `OPENROUTER_API_KEY` for live phrasing checks.
**Estimated effort:** ~3 sessions across 5 phases.

## Open Risks & Assumptions

- The rhythm's *feel* ("friend, not cron") is judged by a human over days — no test can assert it;
  the parameters live in one properties record precisely so they can be tuned.
- SMTP gives no synchronous delivery confirmation; a silently-bouncing address looks like success.
  Accepted — the in-app card is the guaranteed channel at MVP.
- Single always-on machine assumed; if Fly topology ever changes, the in-memory schedule needs
  leader election (explicitly out of scope now).

## Success Criteria (Summary)

- A proposal arrives by itself — email + in-app — at an unpredictable but daytime moment, ~1 per
  2–7 days, and the full US-01 cycle (proposal → answer → first step) runs without any manual
  trigger.
- An ignored proposal is superseded by the next one, its goal snoozed, never re-proposed
  back-to-back.
- Between fires, the backend performs zero database traffic for the scheduler (Neon stays asleep).

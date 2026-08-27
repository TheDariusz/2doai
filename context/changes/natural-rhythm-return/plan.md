# S-05 — Natural Rhythm Return Implementation Plan

## Overview

The AI returns to the user on its own, in a random natural rhythm (~1 proposal per 2–7 days, never
a fixed time), with a proposal for a neglected goal/dream — delivered by email with a link and
visible in the app at next open (FR-011, FR-018, US-01). This is the gwiazda przewodnia slice: it
adds the autonomous loop on top of the S-04 engine, which already does select → phrase → persist.

## Current State Analysis

- `ProposalService.propose()` (`backend/src/main/java/com/thedariusz/todoai/proposal/ProposalService.java:113`)
  contains the whole manual-trigger chain: pending short-circuit → load goals → `ProposalSelector.select`
  → LLM phrase with `ProposalTemplate` fallback → persist (races resolved by the partial unique
  index). It is package-private and reads the user from `CurrentUser.requireId()` — a scheduler
  thread has no `SecurityContext`.
- The `proposal` table (`V8__create_proposal.sql`) enforces FR-018's at-most-one-pending via
  `idx_proposal_one_pending ON proposal (user_id) WHERE answered_at IS NULL` and forbids a fake
  closed state via `CHECK ((answer IS NULL) = (answered_at IS NULL))`. **A pending proposal today is
  permanent until answered** — no superseding path exists.
- There is **no GET endpoint**: the SPA only sees a proposal by POSTing (button press). An emailed
  proposal would be invisible until the user happens to press "Daj mi coś teraz".
- **No scheduling exists** (`@SpringBootApplication` bare, zero `@Scheduled`), and **no email
  infrastructure** (no mail dependency, no provider, no public-URL property).
- Hard constraint (lessons.md "Let a scale-to-zero database actually sleep"): **nothing may touch
  the DB more often than Neon's ~5-min autosuspend window**. Next fire times live in memory; the DB
  is read/written only when actually firing. `fly.toml` already pins the machine always-on for this
  scheduler, and the health check is liveness-only (never DB-touching).
- `USER_ZONE = Europe/Warsaw` is hardcoded at `ProposalService.java:72` (the documented seam);
  no `user.timezone`, no locale, no quiet hours anywhere.
- `UserRepository extends JpaRepository` — `findAll()` exists for user enumeration. Registration
  lives in the `user` package; new users must enter the in-memory schedule without a restart.
- Guards a new slice must satisfy: `ApiSurfaceTest` (both-directions controller↔`openapi.yaml`
  parity), `UserOwnedConventionTest` (structural), per-user deleter + `information_schema` erasure
  test, `CategorySyncCheck`, Flyway expand-only (next: **V9**), `docs/index.html` code-map row per
  new package, CI Trivy gate on new dependencies.

## Desired End State

A logged-out user receives, at an unpredictable moment inside 9:00–21:00 Warsaw time and on average
every 2–7 days, an email in Polish containing the proposal message and a link to
`https://2doai.app/goals`. Opening the app shows the same proposal in the existing card without any
button press; the four answers work unchanged. If a previous proposal was never answered, the new
cycle closes it as `SUPERSEDED` (implicit "nie teraz": its goal is snoozed +3 days) before opening
the new one. A Fly restart does not reset or bunch up the rhythm. Between fires the app performs
zero database traffic on the scheduler's behalf.

Verification: full `/check` green; a REST-visible integration test drives one complete scheduled
cycle against Testcontainers; a human forces a fire in prod-like conditions and receives a real
email (gated live test + manual pass).

### Key Discoveries:

- Scheduler entry point exists but needs a user-argument path: `ProposalService.propose()`
  (`ProposalService.java:113`) + `CurrentUser.requireId()` (`security/CurrentUser.java:29`).
- The clock convention is "read `now` at the service edge, pass it down" — no `java.time.Clock`
  anywhere; `ProposalSelector.select(entries, now)` is the model to copy for the rhythm function.
- `application.properties:68-77` is the template for provider config: vendor prefix + `${ENV:}`
  **empty default** that keeps CI hermetic; `ProposalLiveTest` is the template for a gated live test.
- `DataSourcePoolPropertiesTest` binds the committed properties onto real config classes without
  booting a context — the template for guarding scheduler settings.
- `spring-boot-starter-mail` is in the Boot BOM: SMTP transport adds **zero new supply chain**, and
  Resend speaks SMTP, so the provider stays a credentials choice.
- `infrastructure.md:85` already prescribes scheduler observability: startup log line + liveness
  check that the scheduler is alive.
- The CHECK constraint means superseding must write a real `answer` value — `answered_at` alone
  cannot express "expired".

## What We're NOT Doing

- **No per-user timezone or locale column** — fixed `Europe/Warsaw` + 9:00–21:00 window; the
  upgrade seam stays the documented `ponytail:` comment.
- **No tokenized answer-links in email** — answering happens in the app, logged in.
- **No email retry queue** — send failure is logged; the in-app card is the guaranteed channel.
- **No notification preferences / unsubscribe UI** — single-user MVP; FR-018 makes email part of
  the product loop, not marketing.
- **No S-06 priority categories**, no S-03 onboarding seed, no engagement-adaptive rhythm — the
  rhythm is random by product definition (Guardrails).
- **No changes to the manual trigger** — `POST /api/proposals` keeps its exact semantics.
- **No leader election / distributed locks** — one always-on Fly machine by design.

## Implementation Approach

Everything scheduler-shaped lives in the existing `proposal` package (package-private access to
`ProposalService`, no visibility widening); only the email port gets a new `mail` package. The
rhythm is a pure static function in the `ProposalSelector` mold. Timing state is one nullable
`timestamptz` column on `app_user`, mirrored in a `ConcurrentHashMap` loaded once at startup; a
DB-free in-memory tick (`@Scheduled`, 60s) compares map entries against `now` and only a due entry
inside the send window triggers database work: supersede-if-replacing → select → phrase → persist →
email → draw + persist next fire time. Email goes through a two-method `EmailSender` port backed by
`JavaMailSender` over Resend SMTP. The SPA gains a side-effect-free `GET /api/proposals/pending`
and auto-loads the card on `/goals` mount.

## Critical Implementation Details

- **Supersede ordering** — the scheduled flow must run selection **with the pending proposal's goal
  excluded** and supersede only when that selection produced a replacement. Superseding first would
  snooze the old goal and can leave the user with nothing when selection then comes back empty —
  FR-018's implicit "nie teraz" happens "w chwili, gdy zastępuje ją propozycja", i.e. only on actual
  replacement. This also guarantees the new proposal never names the goal the user just ignored.
- **Scheduled ≠ manual semantics** — the scheduler must NOT reuse `propose()`'s pending
  short-circuit (that is manual-trigger behavior). It gets its own package-private service method;
  the manual path is untouched.
- **`SUPERSEDED` is machine-only** — `POST /{id}/answer` must reject it with 422 exactly like an
  inconsistent `remind_in_days`; otherwise a user can forge a machine closure.
- **DB silence between fires** — the 60s tick reads only the in-memory map. The single startup
  `findAll()` and the writes at fire/registration time are the only DB touches. Nothing
  DB-touching goes anywhere near the liveness health group.
- **Users created after boot** — registration must publish an application event the scheduler
  listens to (draw + persist + put in map); otherwise a new user is invisible until restart. A user
  deleted after boot is dropped from the map when their fire finds no row.
- **Wire-contract duplication** (lessons.md) — `SUPERSEDED` joins the answer enum in Java,
  `openapi.yaml`, and the frontend TS union. Name the duplication where introduced; the SPA never
  renders it (a superseded proposal is by definition not pending), so no copy string is needed.

## Phase 1: Schema, rhythm function, superseding

### Overview

The V9 migration, the pure rhythm draw, the `SUPERSEDED` closure, and a `ProposalService` path a
scheduler can call — everything testable without any scheduling running yet.

### Changes Required:

#### 1. Migration V9

**File**: `backend/src/main/resources/db/migration/V9__scheduler_state.sql`

**Intent**: Add per-user timing state for the rhythm.

**Contract**: `ALTER TABLE app_user ADD COLUMN next_proposal_at timestamptz;` — nullable (null =
never scheduled yet; backfilled by the scheduler at boot), expand-only. No change to `proposal`:
`SUPERSEDED` is a new value in an existing `VARCHAR(16)` column and satisfies the existing CHECK.

#### 2. User timing accessor

**File**: `backend/src/main/java/com/thedariusz/todoai/user/User.java`

**Intent**: Map the new column so the scheduler can read/write it through the aggregate.

**Contract**: field `nextProposalAt` (`OffsetDateTime`, nullable) + getter and a
`scheduleNextProposalAt(OffsetDateTime)` mutator. Hibernate `ddl-auto=validate` makes V9 and this
mapping land together.

#### 3. Rhythm draw

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalRhythm.java` (new)

**Intent**: The natural rhythm as a pure function, testable like `ProposalSelector`.

**Contract**: static-only class;
`static OffsetDateTime next(OffsetDateTime from, RhythmProperties props, Random random)` — draws a
day uniformly 2–7 days after `from`, then a second uniformly inside 9:00–21:00 `Europe/Warsaw` on
that day. Bounds come from a new validated `RhythmProperties` record
(`proposal.rhythm.min-days=2`, `max-days=7`, `window-start-hour=9`, `window-end-hour=21`) following
the `MemoryProperties` pattern (record + config seam + binding test). `Random` is injected for
determinism in tests; production passes a shared `SecureRandom`-free `Random` (randomness is a
product feel, not a security property).

#### 4. SUPERSEDED closure on the aggregate

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/Proposal.java`, `ProposalAnswer.java`

**Intent**: Machine closure distinct from a user answer, honest in memory/stats (FR-010).

**Contract**: `ProposalAnswer.SUPERSEDED` (fifth value); `Proposal.supersede(OffsetDateTime now)`
delegating to the existing single-write guard (`ProposalAlreadyAnsweredException` on a second
closure).

#### 5. Scheduled propose path

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalService.java`

**Intent**: The flow a scheduler thread can run for a given user, with replacement semantics.

**Contract**: package-private `Optional<ProposalResponse> proposeScheduled(UUID userId)`:
load goals → select with the pending proposal's goal excluded from candidates → if empty, return
empty (pending, if any, stays untouched) → else supersede the pending one (write `SUPERSEDED` +
apply the NOT_NOW effect: goal snooze +3 days, episode `proposal_superseded`) → phrase/persist via
the existing `draft()`/`open()` helpers → return the response. `CurrentUser` is not consulted.
The existing no-arg `propose()` keeps its exact behavior.

#### 6. Reject user-submitted SUPERSEDED

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalAnswerRequest.java`

**Intent**: Keep the machine-only value out of the answer endpoint.

**Contract**: bean-validation rejects `answer = SUPERSEDED` with 422, same mechanism as the
existing `@AssertTrue` consistency check.

#### 7. Wire contract for the fifth value

**File**: `context/foundation/openapi.yaml`, `frontend/src/…` (the TS answer union)

**Intent**: Keep spec, Java and TS enumerations aligned in the same commit (project rule; lessons.md
duplication warning — noted in the spec description as machine-written, never user-submittable).

**Contract**: `SUPERSEDED` added to the answer enum in both; no UI copy (never rendered as pending).

### Success Criteria:

#### Automated Verification:

- `mvn test` green — including new `ProposalRhythmTest` (bounds 2–7 days, window 9–21 Warsaw,
  determinism under seeded `Random`), `RhythmPropertiesTest` binding test, aggregate supersede
  test, and a REST Assured test proving: scheduled path supersedes a pending proposal, snoozes its
  goal, opens a replacement naming a different goal, and the answer endpoint 422s on `SUPERSEDED`
- Flyway V9 applies cleanly under Testcontainers (any `@SpringBootTest` boot proves it)
- `ApiSurfaceTest` still green (no endpoint change in this phase)

#### Manual Verification:

- none (pure backend phase)

---

## Phase 2: Scheduler

### Overview

First `@EnableScheduling` in the project: the in-memory schedule, the DB-free tick, boot load,
registration hook, observability — wired to `proposeScheduled` but with email still absent (added
in Phase 3 behind the port).

### Changes Required:

#### 1. Enable scheduling

**File**: `backend/src/main/java/com/thedariusz/todoai/Application.java`

**Intent**: Turn on Spring's scheduling for the one tick.

**Contract**: `@EnableScheduling` on the application class.

#### 2. The scheduler

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalScheduler.java` (new)

**Intent**: Own the in-memory rhythm state and the fire cycle; the only DB traffic it generates is
at boot, at fire, and at (re)scheduling writes.

**Contract**: `@Component` in the `proposal` package. On `ApplicationReadyEvent`: one
`UserRepository.findAll()`, backfill `next_proposal_at` (draw + persist) where null, load all into
`ConcurrentHashMap<UUID, OffsetDateTime>`, log one startup line with the loaded count
(`infrastructure.md:85`). `@Scheduled(fixedDelay = 60_000)` tick: record last-tick instant (for the
health indicator), scan the map, and for entries with `now >= next` **and** `now` inside the send
window: run `proposeScheduled(userId)`, hand a created proposal to the (Phase 3) mailer, draw the
next fire time, persist it on the user row and update the map. A fire for a vanished user removes
the map entry. Listens for a new `UserRegistered` application event → draw + persist + put.

#### 3. Registration event

**File**: `backend/src/main/java/com/thedariusz/todoai/user/…` (registration service)

**Intent**: New users enter the rhythm without a restart, without a `user`→`proposal` dependency.

**Contract**: registration publishes `UserRegistered(UUID userId)` via
`ApplicationEventPublisher`; the record lives in the `user` package, the listener in the scheduler.

#### 4. Scheduler liveness

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/…`, `backend/src/main/resources/application.properties`

**Intent**: The prescribed "scheduler thread is alive" check, DB-free by construction.

**Contract**: a `HealthIndicator` reporting UP while the last tick is recent (a few tick periods),
added to the **liveness** group in `application.properties` (`db` stays readiness-only; fly.toml
probes liveness).

### Success Criteria:

#### Automated Verification:

- `mvn test` green — scheduler unit tests with seeded `Random` and injected `now` covering: due +
  in-window fires; due + out-of-window waits; not-due does nothing **and performs zero repository
  calls** (the Neon-silence guard, mock-verified); vanished user pruned; registration event
  schedules
- Integration test (Testcontainers): boot with a user whose `next_proposal_at` is null → backfilled
  and loaded; forcing a past `next_proposal_at` and invoking the tick body produces a proposal and
  a future `next_proposal_at`
- Health endpoint test: liveness group contains the scheduler indicator and no DB-touching one

#### Manual Verification:

- `mvn spring-boot:run` locally: startup line logs the loaded schedule; no periodic DB queries in
  the SQL log while idle

**Implementation Note**: After completing this phase and all automated verification passes, pause
for manual confirmation before proceeding.

---

## Phase 3: Email delivery

### Overview

First email infrastructure: the `EmailSender` port over Resend SMTP, the Polish proposal email,
failure = log + in-app fallback.

### Changes Required:

#### 1. Mail dependency

**File**: `backend/pom.xml`

**Intent**: SMTP transport with zero new supply chain.

**Contract**: `spring-boot-starter-mail` (managed by the Boot BOM; Trivy-neutral).

#### 2. Email port and SMTP adapter

**File**: `backend/src/main/java/com/thedariusz/todoai/mail/EmailSender.java`, `SmtpEmailSender.java` (new package)

**Intent**: The `LlmClient` pattern for the second external dependency: small port, one adapter,
failures translated and logged without content.

**Contract**: `EmailSender.send(String to, String subject, String text)`; `SmtpEmailSender`
wraps `JavaMailSender`, catches transport exceptions, logs recipient-domain + subject length only
(never body or address local-part), rethrows as `MailDeliveryException`. The scheduler catches it,
logs, and continues — the proposal stays pending for in-app pickup.

#### 3. Provider configuration

**File**: `backend/src/main/resources/application.properties`

**Intent**: Resend over SMTP as pure configuration, hermetic in CI.

**Contract**: vendor prefix `spring.mail.*` — `host=smtp.resend.com`, `port=587`,
`username=resend`, `password=${RESEND_API_KEY:}` (empty default, the load-bearing hermetic
pattern), STARTTLS on. Sender + link base in a new validated `MailProperties`-style record under
prefix `app.mail` (`from=2do AI <propozycje@2doai.app>`, `base-url=${APP_BASE_URL:http://localhost:5173}`).

#### 4. The proposal email

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalEmail.java` (new)

**Intent**: FR-018's content: the proposal message + the app link, text-first, in the language the
server picked (Polish today, same status as `ProposalTemplate`).

**Contract**: static-only builder `subject(Proposal)` / `body(Proposal, String baseUrl)`; body =
the stored proposal message + `<baseUrl>/goals` link. Scheduler sends to `user.getEmail()` after a
successful `proposeScheduled`.

#### 5. Gated live test

**File**: `backend/src/test/java/com/thedariusz/todoai/proposal/ResendLiveTest.java` (new)

**Intent**: One real send for human verification, off by default (the `ProposalLiveTest` template).

**Contract**: `@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")` (plus a
recipient env var); sends one proposal-shaped email and logs the outcome.

### Success Criteria:

#### Automated Verification:

- `mvn test` green — `ProposalEmailTest` (subject/body contain the message and the link),
  scheduler test verifying `EmailSender` invoked on fire and that `MailDeliveryException` leaves
  the proposal pending and the cycle rescheduled; properties binding test for the new record
- CI stays hermetic with no `RESEND_API_KEY` set (empty-default proves itself by the suite passing)

#### Manual Verification:

- Resend account + `2doai.app` domain verified (DNS records on Cloudflare) — **user does this**
- `RESEND_API_KEY=… mvn test -Dtest=ResendLiveTest` delivers a real email whose Polish reads like
  the friend, with a working link

**Implementation Note**: Pause after this phase for the manual email verification before proceeding.

---

## Phase 4: In-app surfacing

### Overview

FR-018's second channel: the pending proposal appears in the app at next open, without a button
press and without side effects.

### Changes Required:

#### 1. GET pending endpoint

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalController.java`, `ProposalService.java`

**Intent**: A side-effect-free read of the current pending proposal.

**Contract**: `GET /api/proposals/pending` → 200 `ProposalResponse` when a pending proposal
exists, 204 otherwise. No selection, no LLM, no writes.

#### 2. Spec parity

**File**: `context/foundation/openapi.yaml`

**Intent**: `ApiSurfaceTest` demands both directions in the same commit.

**Contract**: the new path + response schemas, answer enum already extended in Phase 1.

#### 3. Auto-load on /goals

**File**: `frontend/src/pages/ProposalCard.tsx` (and its test)

**Intent**: The card shows a waiting proposal on mount; the button keeps its manual-trigger role.

**Contract**: on mount, `GET /proposals/pending`; 200 populates the same card state the button
does, 204 leaves the idle state. Vitest: mount with a mocked 200 shows the message and the four
answers; 204 shows the button.

### Success Criteria:

#### Automated Verification:

- `mvn test` green — REST Assured: GET returns the scheduler-created proposal, 204 when none,
  401 anonymous; `ApiSurfaceTest` parity holds
- `npm test`, `npm run lint`, `npm run build` green

#### Manual Verification:

- With a pending proposal in the DB, opening `/goals` shows the card immediately; answering it works
  end-to-end; afterwards the card returns to idle

**Implementation Note**: Pause after this phase for manual UI confirmation before the docs walk.

---

## Phase 5: Documentation & deployment walk

### Overview

The slice is not done until the living documentation describes what merged and prod can actually
send email.

### Changes Required:

#### 1. docs/index.html — the full walk

**File**: `docs/index.html`

**Intent**: Walk the whole CLAUDE.md list, in order — the partial pass reads exactly like a
complete one.

**Contract**: (1) `#overview` capability badges for the proactive loop; (2) `#code-map` row for the
new `mail` package; (3) `#backend` prose + class diagrams: `ProposalScheduler`, `ProposalRhythm`,
`ProposalEmail`, `EmailSender`, `SUPERSEDED`, `User.nextProposalAt`; (4) terms `<dl>`: natural
rhythm, superseding, quiet hours; (5) `#flows`: the automatic cycle now implemented, retire the
"planned" phrasing; (6) `#data`: `app_user.next_proposal_at` prose + re-export ER SVGs (schema
changed; remember the draw.io light-palette fix from memory); (7) `#roadmap` cards + `#glossary`;
(8) endpoint list: `GET /api/proposals/pending`; (9) stamp `Verified against …` with DEV-24.

#### 2. Roadmap, change, runbook

**File**: `context/foundation/roadmap.md`, `context/changes/natural-rhythm-return/change.md`, `context/foundation/deployment-runbook.md`

**Intent**: Close the slice in the planning surfaces and make prod email reproducible.

**Contract**: roadmap S-05 status + fast-path note; `change.md` status transition; runbook
addendum: Resend signup, Cloudflare DNS records for `2doai.app`, `fly secrets set RESEND_API_KEY`
+ `APP_BASE_URL=https://2doai.app` (remember: `gh`/`fly` secret commands need a real TTY).

### Success Criteria:

#### Automated Verification:

- `node --test docs/index.test.mjs` green
- `/check` fully green (backend, frontend tests + lint + build, docs)

#### Manual Verification:

- Read `docs/index.html` in a browser: diagrams render, ER SVGs show `next_proposal_at`, the nine
  walk items each reflect the merged reality
- Prod smoke after deploy: startup log shows the schedule loaded; forcing one user's
  `next_proposal_at` into the past produces a real email and the card at `https://2doai.app/goals`

---

## Testing Strategy

### Unit Tests:

- `ProposalRhythmTest` — interval bounds, window bounds, Warsaw zone, seeded determinism
- Scheduler tick — due/not-due/out-of-window, zero-DB-when-idle (mock-verified), prune on missing
  user, registration event
- `Proposal.supersede` single-write guard; `ProposalAnswerRequest` 422 on `SUPERSEDED`
- `ProposalEmailTest`; `SmtpEmailSender` failure translation (no content in logs)

### Integration Tests:

- End-to-end scheduled cycle under Testcontainers: pending proposal + past `next_proposal_at` →
  tick → old superseded (+3d goal snooze), new proposal for a different goal, email port invoked,
  future `next_proposal_at` persisted
- `GET /api/proposals/pending` REST Assured suite; `ApiSurfaceTest`; erasure test still green
  (`next_proposal_at` dies with the `app_user` row)

### Manual Testing Steps:

1. Run backend locally, watch the startup schedule line; confirm SQL log silence while idle
2. Set a user's `next_proposal_at` to the past inside the window; observe fire, email (live key),
   and the card on next `/goals` open
3. Leave the proposal unanswered, force another fire: old one superseded, its goal not re-proposed
4. Register a fresh user: appears in the schedule without a restart

## Performance Considerations

The tick is O(users) over an in-memory map every 60s with zero I/O — irrelevant at MVP scale. The
startup `findAll()` is one query per boot. Everything else already existed (one Sonnet call per
fire). The Neon idleness budget is the constraint that shaped the design; the zero-DB-idle test is
its regression guard.

## Migration Notes

V9 is expand-only (one nullable column) — safe under image rollback. Existing users get
`next_proposal_at` backfilled by the first boot's scheduler load; no data migration needed.
`SUPERSEDED` needs no schema change.

## References

- Prior slice: `context/changes/proactive-proposal-engine/plan.md` (+ `plan-brief.md`)
- Constraints: `context/foundation/lessons.md` (Neon idleness), `context/foundation/roadmap.md:238-251`
- Engine seams: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalService.java:113`,
  `ProposalSelector.java:114`, `V8__create_proposal.sql:57`
- Config patterns: `backend/src/main/resources/application.properties:68-77`,
  `backend/src/test/java/com/thedariusz/todoai/DataSourcePoolPropertiesTest.java`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Schema, rhythm function, superseding

#### Automated

- [x] 1.1 `mvn test` green — rhythm, binding, supersede, scheduled-path REST test, 422 on SUPERSEDED
- [x] 1.2 Flyway V9 applies cleanly under Testcontainers
- [x] 1.3 `ApiSurfaceTest` still green

### Phase 2: Scheduler

#### Automated

- [ ] 2.1 `mvn test` green — tick semantics incl. zero-DB-when-idle guard, prune, registration event
- [ ] 2.2 Integration: null backfill at boot; forced past fire produces proposal + future next_proposal_at
- [ ] 2.3 Liveness group contains the scheduler indicator, nothing DB-touching

#### Manual

- [ ] 2.4 Local run: startup schedule line; SQL log silent while idle

### Phase 3: Email delivery

#### Automated

- [ ] 3.1 `mvn test` green — email builder, send-on-fire, failure leaves proposal pending, binding test
- [ ] 3.2 CI hermetic without RESEND_API_KEY

#### Manual

- [ ] 3.3 Resend account + 2doai.app domain verified (user)
- [ ] 3.4 Gated live test delivers a real Polish email with a working link

### Phase 4: In-app surfacing

#### Automated

- [ ] 4.1 `mvn test` green — GET pending suite + ApiSurfaceTest parity
- [ ] 4.2 `npm test`, `npm run lint`, `npm run build` green

#### Manual

- [ ] 4.3 /goals shows the waiting card on open; answering works; idle state returns

### Phase 5: Documentation & deployment walk

#### Automated

- [ ] 5.1 `node --test docs/index.test.mjs` green
- [ ] 5.2 `/check` fully green

#### Manual

- [ ] 5.3 docs page read in browser — all nine walk items current, ER SVGs re-exported
- [ ] 5.4 Prod smoke: schedule loads, forced fire delivers email + card on 2doai.app

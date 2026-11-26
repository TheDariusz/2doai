# Task Time-of-Day and Due-Date Reminder (DEV-63) Implementation Plan

## Overview

A `TASK` gains an optional time of day and a per-task reminder lead. A new, separate reminder
mechanism e-mails the user once, on the task's due date, at a moment the **user** set. It never
touches the pending proposal, the natural rhythm, or AI memory. The goal is to make 2doai a place a
time-bound commitment can be trusted to (frame: `context/changes/due-date-reminder/frame.md`).

## Current State Analysis

- `goal.due_date DATE` (`V7__goal_due_date.sql:10`) is the only time field a task has. The
  layer × time-fields rule is enforced at three depths: the DTO `@AssertTrue`
  (`GoalCreation.java`, `GoalUpdate.java`), `Goal.update` / `Goal.hasConsistentTimeFields`
  (`Goal.java`), and the `chk_goal_layer_time_fields` CHECK (`V7`).
- Nothing fires because of a date. The only outbound sender is `ProposalScheduler.fire`
  (`ProposalScheduler.java`), and the due date only biases `ProposalSelector` **after** it has passed.
- The proposal pipeline cannot carry a reminder. `proposeScheduled` supersedes the pending proposal,
  writes a "not now" memory episode and resets the rhythm (frame, hypothesis 3).
- `GoalService` publishes only `GoalLeftTheRunning` (complete/withdraw transition). There are no
  create/update/delete events.
- `ProposalScheduler` is the proven pattern for timers on a scale-to-zero DB: an in-memory map backed
  by a column, one query at boot, a 60s I/O-free tick, `PerUserDataDeleter` for erasure, and a
  liveness `HealthIndicator` (`application.properties:75`).
- `ProposalRhythm.USER_ZONE` (Europe/Warsaw) is the single place the app decides whose day it means.
- `User.getLanguage()` gives the account's `AppLanguage`. Per-locale mail copy follows
  `VerificationEmailPl` / `VerificationEmailEn`.
- SPA: one `<input type="date" name="due_date">` (`GoalsPage.tsx:420`) and a `due` chip (`:548`).
  `GoalApiTest.publishesTheWireEnumsTheContractAnchors` (`GoalApiTest.java:599`) holds wire enums
  across `openapi.yaml`, Java and `GoalsPage.tsx`.

## Desired End State

- A task can carry `due_time` (`"HH:mm"`, only together with `due_date`) and, when timed, a
  `reminder_lead` ∈ {`AT_TIME`, `FIFTEEN_MINUTES`, `ONE_HOUR`, `MORNING_OF`}, defaulting to
  `ONE_HOUR`. A `GOAL` or `DREAM` carries none of these. All three depths reject violations.
- Every active task with a due date gets exactly one reminder e-mail, in the account's language:
  - **date-only** → at the morning hour (08:00 Europe/Warsaw, configurable) of the due day;
  - **timed** → at `due moment − lead`, or at the morning hour for `MORNING_OF`, never later than
    the due moment itself.
- A missed reminder moment (downtime, or the task was saved after its moment) still sends while the
  task's **due moment**, plus a short grace that absorbs the tick period, has not passed. The due
  moment is the time for a timed task and the end of the due day for a date-only one. After that the
  reminder is dropped silently, including for an overdue task that is reopened, restored or created
  with a past date.
- Changing date, time or lead re-arms the reminder. Completing, withdrawing or deleting cancels it.
  Reopening or restoring re-arms it only if it was never sent for the current term.
- The database is touched at boot (one query), on goal writes (already happening), and on fire.
  Never by the tick.
- PRD and `docs/index.html` state the line: *the AI owns the moment of a proposal; the user owns the
  moment of a reminder.*

### Key Discoveries:

- `Goal.update` is the single write path (constructor delegates to it): `Goal.java` `update(...)`.
  Term changes and the sent-marker reset belong there.
- `GoalUpdate` is full-replace. New fields are nullable objects (not primitives), so they do not
  become a breaking 400 the way `withdrawn` did (`GoalUpdate.java` javadoc).
- `ProposalScheduler.reschedule` / `fire` (`ProposalScheduler.java`): "map first, column second,
  nothing throws past here", and a `RuntimeException` must never escape `forEach`.
- Lock order is goal row first, then proposal (`GoalRepository.findWithLockByIdAndUserId`). The
  reminder fire takes only the goal row.
- `GoalRepository` deliberately extends bare `Repository`, so every finder is user-scoped. The boot
  load is cross-user and must be a separately named, documented finder.
- Liveness group: `management.endpoint.health.group.liveness.include=livenessState,proposalScheduler`.

## What We're NOT Doing

- Web Push or any channel other than e-mail (FR-018 note: post-MVP).
- Action links in the mail ("mark done", snooze). The mail links to `/goals` only.
- A morning digest. Reminders go out one mail per task.
- A per-user morning hour or timezone. The zone stays `ProposalRhythm.USER_ZONE`.
- `DAY_BEFORE`, `NONE`, or a reminder control for date-only tasks.
- Retry of a failed reminder send (at-most-once; see Critical Implementation Details).
- Recurrence, overdue alarms, or splitting `Goal` into a separate `Task` aggregate.
- Exposing `reminder_sent_at` on the wire.
- Any change to the proposal pipeline, rhythm or `ProposalSelector` bias.

## Implementation Approach

Mirror `ProposalScheduler` in a new `reminder` bounded context that `goal` does not know about.
`goal` publishes value-only events (`GoalTermChanged`, `GoalDeleted`) inside its write transaction.
`reminder` keeps `Map<goalId, PendingReminder>` in memory and computes moments with a pure
function. It fires through a short transaction that stamps `goal.reminder_sent_at`, and then mails
outside it. Contract first (phase 1), so the scheduler builds on stable fields, then the SPA, then
the docs.

## Critical Implementation Details

**Fire ordering is at-most-once on purpose.** The fire locks the goal row, re-checks it (active,
unsent, term unchanged), stamps `reminder_sent_at`, and commits. Only then does it send, outside
any transaction and without holding a connection, per the `EmailSender` contract. A send failure
is logged at ERROR (visible in Loki as `service="2doai"`, `level=error`) and not retried. A retry
loop would query Neon every tick for as long as the mail provider is down. The in-app due chip is
the second channel.

**The moment and the expiry are both derived from the term, not stored.** Only `reminder_sent_at` is
persisted. The map entry is `(userId, moment, expiry, notBefore)`, where **expiry = due moment +
`reminder.late-grace`** (default `PT5M`, validated to be at least `PT4M`). The floor is not just the 60s tick: the reminder
tick shares Boot's single scheduler thread with the proposal fire, which can block it for about
2.5 minutes (`spring.ai.openai.timeout=60s` × 2 attempts via `max-retries=1`, plus the three 10s `spring.mail` timeouts, all in `application.properties`; re-check this floor whenever those change). The grace is what
lets `AT_TIME`, and a `MORNING_OF` clamped to the due moment, fire at all: their moment *is* the due
moment, and a 60s tick almost never lands on it exactly. The tick fires entries with
`max(moment, notBefore) ≤ now < expiry` and silently prunes entries with `now ≥ expiry`, so the two conditions never
overlap. The boot load and the event listener apply the same rule: a term whose expiry has already
passed is never put in the map. Pruning needs no DB write, because an unsent, expired term is simply
not loaded again at the next boot (the boot query filters on expiry).

**Events are published after the term is applied and carry values, not the entity.** This follows
`GoalLeftTheRunning`'s reasoning. `GoalTermChanged(userId, goalId, dueDate, dueTime, lead, active,
reminderSent)` is enough for the listener to upsert or remove a map entry with zero queries.
`GoalDeleted(userId, goalId)` removes it. Unlike `GoalLeftTheRunning` (whose listener must write in
the same transaction), the reminder listeners only touch memory, so they are
`@TransactionalEventListener(phase = AFTER_COMMIT)`, after the `RegistrationAuditLogger` precedent.
A rolled-back write then changes nothing in the map: no stale entry, and no removal of an entry the
row still has.

**Writes that bypass `GoalService` emit no event, and that is accepted.** A proposal answered
`NEVER` withdraws the entry in `ProposalService` (`ProposalService.java:398`), and account erasure
deletes goals through `GoalDataDeleter`. `ReminderScheduler` implementing `PerUserDataDeleter` covers
account erasure. The `NEVER` path leaves a map entry that the fire re-check (inactive) drops, at
the cost of one short query at its moment. Publishing from `ProposalService` is not worth the
coupling.

**`due_time` wire format.** Jackson writes `LocalTime` as `HH:mm:ss` by default. Pin it to `HH:mm`
(e.g. `@JsonFormat(pattern = "HH:mm")` on the DTO/response components) and truncate to minutes in
`Goal.update`, so `<input type="time">`'s value round-trips byte-identical.

## Phase 1: Term Model and Contract

### Overview

The task can store a time of day and a reminder lead. The invariant is extended at all three depths,
and the contract (spec + Java + TS type) moves in one commit.

### Changes Required:

#### 1. Migration

**File**: `backend/src/main/resources/db/migration/V18__goal_due_time_and_reminder.sql`

**Intent**: Add the three nullable columns and widen the layer × time-fields CHECK. Expand-only:
the previous image does not map the columns, and every row it *creates* satisfies the new CHECK.
Rollback has one known edge (see Migration Notes).

**Contract**: `goal.due_time TIME NULL`, `goal.reminder_lead VARCHAR(16) NULL`,
`goal.reminder_sent_at TIMESTAMPTZ NULL`. Replace `chk_goal_layer_time_fields` (drop + add, same
name), layer-major as in `V7`:
`GOAL` → horizon set, due_date/due_time/reminder_lead null; `DREAM` → all null; `TASK` → horizon
null, `due_time IS NULL OR due_date IS NOT NULL`, `(due_time IS NULL) = (reminder_lead IS NULL)`.
Add a separate `chk_goal_reminder_lead` restricting `reminder_lead` to the four literals.

#### 2. Reminder lead enum

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/ReminderLead.java`

**Intent**: The four leads as a domain enum owned by `goal` (it is a field of the entry). `reminder`
interprets it.

**Contract**: `public enum ReminderLead { AT_TIME, FIFTEEN_MINUTES, ONE_HOUR, MORNING_OF }`, with
`ONE_HOUR` documented as the default for a timed task.

#### 3. Aggregate

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/Goal.java`

**Intent**: Map `dueTime` (`LocalTime`), `reminderLead` (`@Enumerated STRING`) and `reminderSentAt`.
Extend `update(...)` and the constructor with `dueTime` and `reminderLead`. When a time is given
without a lead, default to `ONE_HOUR`. Reject a lead without a time. Truncate the time to minutes.
If date, time or lead changed, clear `reminderSentAt`. This is a user write, so moving
`updated_at` is correct. There is deliberately **no** entity method that stamps the sent marker
(see phase 2 §5). Update the invariant table in the javadoc and `TIME_FIELDS_RULE`.

**Contract**: `hasConsistentTimeFields(layer, horizon, dueDate, dueTime, reminderLead)`. The extra
conjuncts: `dueTime == null || dueDate != null` and `reminderLead == null || dueTime != null` (a
missing lead with a time is allowed on input and filled with the default). `update(content, layer,
horizon, dueDate, dueTime, reminderLead, category)` returns `boolean`, which is true when the date,
time or lead changed, so `GoalService` knows whether to publish. Add the 8-argument constructor and
**keep the existing 6-argument constructor** as an overload that delegates with a `null` time and
lead. That way the ~25 positional `new Goal(...)` calls in the proposal, account and persistence
tests stay untouched. Only `GoalService` and the two `update` calls in `GoalPersistenceTest`
(`:150`, `:154`) change.

#### 4. DTOs and response

**Files**: `goal/GoalCreation.java`, `goal/GoalUpdate.java`, `goal/GoalResponse.java`

**Intent**: Carry `dueTime` and `reminderLead` both ways. `@AssertTrue` delegates to the widened
rule. The response publishes the stored lead (the defaulted value included).

**Contract**: snake_case `due_time` (`"HH:mm"`, pinned format) and `reminder_lead`, both nullable.

#### 5. Service wiring

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/GoalService.java`

**Intent**: Pass the new fields through `create` and `update`. Event publication is added in
phase 2; this phase only stores the fields.

**Contract**: unchanged method signatures.

#### 6. API contract

**File**: `context/foundation/openapi.yaml`

**Intent**: New `GoalDueTime` (string, pattern `^([01]\d|2[0-3]):[0-5]\d$`) and `ReminderLead`
(`x-extensible-enum`) schemas, referenced from `GoalCreation`, `GoalUpdate` and `Goal`. Update the
layer-rule prose (lines ~529, ~576, ~1175) with the time and lead rules and the `ONE_HOUR` default.

#### 7. SPA type

**File**: `frontend/src/pages/GoalsPage.tsx`

**Intent**: Extend the `Goal` type and `GoalDraft` with `due_time: string | null` and
`reminder_lead: 'AT_TIME' | 'FIFTEEN_MINUTES' | 'ONE_HOUR' | 'MORNING_OF' | null`. Keep the union
inline on one line inside `export type Goal`, because `GoalApiTest.unionLiterals` reads only that
line. Carry both fields through **both** write paths so an edit does not wipe them. The first is
`draftOf`, used by the toggle and restore buttons. The second is the edit form's `submit()`, which
builds its draft from `FormData`. Until phase 3 adds the inputs, it echoes the stored
`goal?.due_time` / `goal?.reminder_lead` only when the submitted layer is `TASK` and a `due_date` is
present, and sends `null` otherwise. Echoing them after the user cleared the date or switched layer
would be a 422 ("time without a date"). No new UI in this phase. Add a
`GoalsPage.test.tsx` case: editing the content of an entry that has a time sends its `due_time` and
`reminder_lead` back unchanged, and clearing its date sends `null` for both.

#### 8. Tests (written first)

**Files**: `backend/src/test/java/com/thedariusz/todoai/GoalApiTest.java`,
`backend/src/test/java/com/thedariusz/todoai/goal/GoalPersistenceTest.java`, a `Goal` unit test if
one is warranted.

**Intent**:
- API: round-trip `due_time` as `"HH:mm"`; a time without a lead returns `ONE_HOUR`; 422 for a time
  on `GOAL`/`DREAM`, a time without a date, and a lead without a time; 400 for an unknown lead literal.
- Persistence: the CHECK rejects each forbidden combination via raw SQL.
- Aggregate: changing the term clears `reminderSentAt`, and an unchanged-term edit keeps it.
- Extend `publishesTheWireEnumsTheContractAnchors` with `ReminderLead` across `openapi.yaml`, Java and
  the SPA union literal.

### Success Criteria:

#### Automated Verification:

- Backend suite passes, including the new API/persistence cases and the widened wire-enum guard: `cd backend && mvn test`
- Flyway applies `V18` and Hibernate `ddl-auto=validate` boots (covered by the Testcontainers suite above): `cd backend && mvn test -Dtest=GoalPersistenceTest`
- Frontend typechecks and builds with the extended type: `cd frontend && npm run build`
- Frontend lint and tests stay green: `cd frontend && npm run lint && npm test`

#### Manual Verification:

- None for this phase.

**Implementation Note**: After all automated verification passes, continue to phase 2. This phase
has no manual gate.

---

## Phase 2: Reminder Scheduler and Mail

### Overview

The `reminder` bounded context fires one localized e-mail per dated task at the user-set moment,
with timing held in memory.

### Changes Required:

#### 1. Goal events

**Files**: `goal/GoalTermChanged.java`, `goal/GoalDeleted.java`, `goal/GoalService.java`

**Intent**: Publish `GoalTermChanged` on create of a dated task, after `saveAndFlush` and from the
saved entity (the UUID v7 id does not exist before), and on update when the term changed
or the active state flipped (either direction). Publish `GoalDeleted` on a successful delete. Both
are value-only records with `requireNonNull` on the ids, as `GoalLeftTheRunning` does.
`GoalLeftTheRunning` stays as is for proposals.

**Contract**: `GoalTermChanged(UUID userId, UUID goalId, LocalDate dueDate, LocalTime dueTime,
ReminderLead lead, boolean active, boolean reminderSent)`, `GoalDeleted(UUID userId, UUID goalId)`.

#### 2. Reminder properties

**Files**: `reminder/ReminderProperties.java`, `backend/src/main/resources/application.properties`

**Intent**: `@ConfigurationProperties(prefix = "reminder")` record with the morning hour, validated
as 0–23, and the late grace, validated as at least 4 minutes (see Critical Implementation
Details), in the `RhythmProperties` mould.

**Contract**: `reminder.morning-hour=8`, `reminder.late-grace=PT5M`.

#### 3. Moment function

**File**: `reminder/ReminderMoment.java`

**Intent**: A pure function (no clock, no DB) that turns a term into `(moment, expiry)` in
`UserZone.ZONE`. Date-only gives morning hour → start of next day + grace. Timed gives
`dueAt − lead`, or the morning hour for `MORNING_OF`, clamped to `≤ dueAt` → `dueAt` + grace. It resolves
local times with `atZone` (DST-safe, as `ProposalRhythm.next` does).

**Contract**: `static Optional<Window> of(LocalDate dueDate, LocalTime dueTime, ReminderLead lead,
ReminderProperties props)`, empty when there is no due date. `USER_ZONE` is package-private in
`proposal`. Lift it to `user/UserZone.java` (`public final class UserZone { public static final
ZoneId ZONE = ZoneId.of("Europe/Warsaw"); }`), because the zone answers whose day it is. Keep
`ProposalRhythm.USER_ZONE` as an alias (`static final ZoneId USER_ZONE = UserZone.ZONE`), so its 3
main and 5 test users need no edit. `UserZone` stays the one place the zone is decided.

#### 4. Scheduler

**File**: `reminder/ReminderScheduler.java`

**Intent**: `Map<UUID goalId, PendingReminder(userId, moment, expiry, notBefore)>`. Load once on
`ApplicationReadyEvent` from a cross-user finder for active, unsent tasks of verified users whose
expiry has not passed, using `putIfAbsent` so it never overwrites an entry a listener already wrote
during startup. A `@Scheduled(fixedDelay = 60s)` tick fires due entries and prunes expired
ones, with no I/O otherwise. `@TransactionalEventListener(AFTER_COMMIT)` on `GoalTermChanged` upserts the entry or removes it
(when inactive, sent, undated, or already past expiry). `GoalDeleted` removes it. `PerUserDataDeleter` removes all of a
user's entries. It implements `HealthIndicator` with the same `lastTick` pulse as
`ProposalScheduler`. No rhythm window applies, because the user chose the moment.

**Contract**: bean name `reminderScheduler`, added to
`management.endpoint.health.group.liveness.include`. `fireDue(OffsetDateTime now)` is package-visible
for tests. Each fire is wrapped so a `RuntimeException` never escapes the iteration. The entry is
removed **before** the fire with the conditional `remove(goalId, entry)`. That way it never re-fires
every tick, and a re-arm that lands while the mail is being sent survives, because its `put`
replaces the entry. The map is a `ConcurrentHashMap`, like `ProposalScheduler.schedule`.
**A failed fire transaction re-arms; a failed send does not.** If the lock-and-stamp transaction
throws (a Neon connection error, for example), `reminder_sent_at` is still null. The entry is then
put back unchanged except for `notBefore = now + 1 min` (`putIfAbsent`, so a newer re-arm wins),
and only while that is before its expiry. The tick fires an entry when
`max(moment, notBefore) ≤ now < expiry`. `notBefore` is null on a fresh entry. That is at most a handful of attempts, each one a query that the outage was going
to make fail anyway. Once the transaction has committed, a send failure is logged at ERROR and is
final (at-most-once).

#### 5. Fire transaction

**Files**: `reminder/ReminderService.java`, `goal/GoalRepository.java`, `goal/PendingReminderRow.java`

**Intent**: In one short transaction: lock the goal by id and user, re-check it (TASK, active,
`reminderSentAt == null`, and the row's term still yields the entry's `moment` and `expiry`. A
`notBefore` set by a re-arm is not part of the term, so it never fails this check), then stamp
`reminder_sent_at` and read the account's address and language. **The stamp must not move
`goal.updated_at`.** `ProposalSelector` reads that column as the last time the user engaged with the
entry (`ProposalSelector.java:55-72`), and machine bookkeeping on the goal row would reset the
rhythm's neglect clock. So the stamp is a `@Modifying` JPQL `update Goal g set g.reminderSentAt =
:now where g.id = :id and g.userId = :userId and g.reminderSentAt is null`. A bulk update bypasses
`@UpdateTimestamp`. The locked entity is only read, never dirtied, so no flush touches it. The
method's javadoc gives this reason. The service returns a sealed `FireOutcome`: `Send(ReminderMail)`, `Rearm(window)` (a stale entry, see below), or `Drop` (inactive, sent, deleted, or expired). An exception propagates to the scheduler, which applies the `notBefore` retry. The mail is
sent after commit. `ReminderService` lives in `reminder` and depends on `goal`, never the reverse.
It uses `GoalRepository.findWithLockByIdAndUserId`, the new `GoalRepository.markReminderSent(id,
userId, now)` (the update above, returning the row count), and
`UserRepository.findById` for the address and language. **A stale entry self-corrects.** If the
locked row is an active, unsent task whose term differs from the entry's, nothing is sent. The
service returns the row's current window instead, and the scheduler re-arms it with `putIfAbsent`
if it has not expired. That case happens when two edits' AFTER_COMMIT listeners run in the reverse
of commit order, or when the boot load read a row before a commit that landed during startup. Add the boot finder to `GoalRepository` with
javadoc explaining why it is the one
unscoped read: a background job, not a request, and it returns a projection, not an entity. It
mirrors
`ProposalScheduler.loadSchedule`'s use of `users.findAll()`.

**Contract**: `List<PendingReminderRow> findPendingReminders(LocalDate fromDay)`, where
`goal/PendingReminderRow` is a public record `(UUID goalId, UUID userId, LocalDate dueDate,
LocalTime dueTime, ReminderLead lead)` built by a JPQL constructor expression, filtering
`layer = TASK`, `due_date >= fromDay`, `reminder_sent_at IS NULL`, `completed_at IS NULL`,
`withdrawn_at IS NULL`, joined to verified users. `fromDay` is **yesterday** in `UserZone.ZONE`,
because a late-evening term's grace can cross midnight. `Goal.userId` is a plain UUID column, so the join
is an explicit JPQL entity join (`join User u on u.id = g.userId where u.emailVerifiedAt is not
null`). The in-memory expiry check removes the remaining past-expiry rows.

#### 6. Reminder e-mail

**Files**: `reminder/ReminderEmail.java` plus per-locale copy (`ReminderEmailPl`, `ReminderEmailEn`,
or a switch over `AppLanguage` in the `CategoryController` mould)

**Intent**: Plain text, like `ProposalEmail`. The subject names the entry (abbreviated to ~60 chars).
The body gives the entry, its due date (and time, if set) formatted for the locale, and a link to
`<baseUrl>/goals`. No action links. User-facing copy is localized; comments and tests refer to the
behaviour, never to the Polish wording.

**Contract**: `subject(AppLanguage, content)`, `body(AppLanguage, content, dueDate, dueTime, baseUrl)`.

#### 7. Tests (written first)

**Files**: `reminder/ReminderMomentTest.java`, `reminder/ReminderSchedulerTest.java`,
`reminder/ReminderSchedulerIntegrationTest.java`, `reminder/ReminderEmailTest.java`

**Intent**:
- Moment: date-only → 08:00. Each lead on a 12:00 task. A lead that crosses midnight: 00:30 +
  `ONE_HOUR` → 23:30 on the previous day, because the lead is subtracted from the **zoned** due
  moment, never from the `LocalTime`. `MORNING_OF` at 07:00 clamps. DST-change days.
- Scheduler (unit, mocked collaborators, as `ProposalSchedulerTest` does): fires at the moment, fires
  late before expiry, drops after expiry, never queries on an idle tick, removes on
  complete/withdraw/delete/account delete, re-arms on a term change, does not re-fire after reopen
  of a sent task, one failing fire does not stop the others, an entry re-armed during a fire is
  still in the map afterwards, `AT_TIME` fires on the first tick after its moment, a reopened
  weeks-overdue task is never armed, and a failed fire transaction re-arms once within expiry while
  a failed send does not.
- Integration (Testcontainers): a task saved through the API ends up with `reminder_sent_at` stamped
  and one `EmailSender.send` to the owner's address after `fireDue(moment)`. The real tick runs in the
  shared test context, with one shared `RecordingEmailSender`. So this test uses a due date at least
  two days ahead, drives the fire only through `fireDue` with a synthetic `now`, and asserts mails by
  the owner's address, never by the total count. No other test may create a task due today, or the
  real tick would mail it. The pending proposal row
  and `app_user.next_proposal_at` are unchanged, **`goal.updated_at` is unchanged**, and no
  `ai_memory_episode` row is written. A stale entry, armed with an older term, re-arms to the row's
  current window instead of being dropped.
- Mail: PL and EN subject/body contain the entry, the date, the time when set, and the `/goals` link.

### Success Criteria:

#### Automated Verification:

- Backend suite passes, including all new reminder tests: `cd backend && mvn test`
- Reminder isolation from proposals holds (no proposal, rhythm, memory or goal.updated_at write on fire): `cd backend && mvn test -Dtest=ReminderSchedulerIntegrationTest`
- Liveness group includes the reminder pulse: `HealthProbeApiTest` asserts `groups.get("liveness").isMember("reminderScheduler")` and that the contributor exists, next to the existing `proposalScheduler` assertions: `cd backend && mvn test -Dtest=HealthProbeApiTest`

#### Manual Verification:

- A reminder for a timed task arrives on the phone in the inbox (not spam) at the expected moment, readable in its account language (human: needs a real mailbox and a real device, see `lessons.md` "Delivered is not delivered").

**Implementation Note**: After automated verification passes, pause for the human to confirm the
real-mailbox check before moving on.

---

## Phase 3: SPA Form and Chip

### Overview

The user can enter a time and pick a lead on a task, and sees the time on the due chip.

### Changes Required:

#### 1. Form controls

**File**: `frontend/src/pages/GoalsPage.tsx`

**Intent**: For `TASK`, show `<input type="time" name="due_time">` only once a date is set. Once a
time is set, show a lead `<select name="reminder_lead">` with the four options, preselected to
`ONE_HOUR` (or the entry's stored lead). Clearing the date clears the time, and clearing the time
clears the lead. The date input is uncontrolled today (`defaultValue` + `FormData`), so the cascade
needs `dueDate` and `dueTime` as React state, initialized from `goal` like `layer`. Reset both next
to `setLayer(defaultLayer)` after a successful submit. Switching the layer away from `TASK` unmounts
the controls, so their values drop out of `FormData` and the payload sends `null`, as `due_date` does
today. The submit mapping sends `null` for empty values.

**Contract**: payload fields `due_time` (`"HH:mm"` | null) and `reminder_lead` (literal | null).

#### 2. Due chip

**File**: `frontend/src/pages/GoalsPage.tsx`

**Intent**: The chip shows the time next to the date when set (new i18n key with both values).

#### 3. Copy

**Files**: `frontend/src/i18n/pl.ts`, `frontend/src/i18n/en.ts`

**Intent**: Labels for the time field, the lead select, each of the four leads, and the dated+timed
chip.

#### 4. Tests (written first)

**File**: `frontend/src/pages/GoalsPage.test.tsx`

**Intent**: The time input appears only with a date. The lead select appears only with a time and
defaults to `ONE_HOUR`. Submit sends `due_time`/`reminder_lead`, and nulls when cleared. Editing a
timed task preserves both. The chip renders the time.

### Success Criteria:

#### Automated Verification:

- Frontend tests pass: `cd frontend && npm test`
- Lint and typecheck/build pass: `cd frontend && npm run lint && npm run build`
- Full gate green: `/check`
- Form works end to end: with backend + `npm run dev` running, sign in as a verified local account (register through the SPA, then `UPDATE app_user SET email_verified_at = now() WHERE email = '<address>'` in the dev Postgres, then sign in with the password), and create a task with date + 12:00 + `ONE_HOUR` at 375px width. `GET /api/goals` returns the entry with `"due_time":"12:00","reminder_lead":"ONE_HOUR"`, the chip shows the time, no horizontal scroll, and the console is free of errors.

#### Manual Verification:

- The time and lead controls feel right on a phone, in both locales (human: subjective UX judgement on a real device).

**Implementation Note**: After automated verification passes, pause for the human's UX check before
moving on.

---

## Phase 4: PRD and Living Documentation

### Overview

The docs describe what merged, and state the proposal/reminder ownership line so the reminder
does not read as contradicting the natural-rhythm guardrail.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: Add a note under the natural-rhythm guardrail (~line 53) and FR-011 (~line 120): a
due-date reminder fires at a moment the user set on their own task. It is a separate mechanism, not
a proposal, and does not count toward the rhythm. *The AI owns the moment of a proposal; the user
owns the moment of a reminder.*

#### 2. Data model

**Files**: `context/foundation/data-model.md`, and the drawio/SVG exports only if the ER diagrams
show `goal` columns (apply the light-palette fix from memory after export)

**Intent**: Document the three new columns and the widened CHECK.

#### 3. Living document

**File**: `docs/index.html`

**Intent**: Walk all nine surfaces from CLAUDE.md "Living documentation", in order. The overview
badge for reminders. A `#code-map` row for the new `reminder` package. `#backend` prose and class
diagram (`Goal` fields, `ReminderLead`, `ReminderScheduler`, events). Terms `<dl>` ("reminder",
"reminder lead"). `#flows` (a new reminder flow, and correct the "not a fixed-time alarm" lines at
~191, ~736, ~2997 to state the ownership line). `#data` prose. `#roadmap` and `#glossary` ("Natural
rhythm" now distinguishes reminders). The endpoint list (unchanged paths, new fields). Stamp the
`Verified against …` note with DEV-63. Diagram edits use only Mermaid constructs already in the file,
with no `;` in sequence message text.

### Success Criteria:

#### Automated Verification:

- Docs structure test passes: `node --test docs/index.test.mjs`
- Mermaid diagrams render: serve `docs/` and load `docs/index.html` with Playwright. Every Mermaid block renders an SVG with no parse error in the console (procedure from memory "Mermaid diagrams can be verified headlessly").
- Every one of the nine surfaces mentions the change where applicable: in the same Playwright session as the Mermaid check, read `document.getElementById(id).textContent` for `overview`, `code-map`, `backend`, `flows`, `data`, `roadmap` and `glossary`. Each must contain its expected term, case-insensitively: `overview` → `reminder` and `DEV-63` (in the `Verified against` note); `code-map` → `reminder`; `backend` → `ReminderScheduler`; `flows` → `reminder`; `data` → `reminder_sent_at`; `roadmap` and `glossary` → `reminder`. Since "reminder" already appears 3 times in the page, record each section's count before the change. Each count must grow.
- Full gate green: `/check`

#### Manual Verification:

- The ownership line reads as consistent with the natural-rhythm principle in the PRD and on the page (human: product-owner wording judgement).

---

## Testing Strategy

### Unit Tests:

- `ReminderMoment` as a pure function: every lead, date-only, the clamp, DST transitions.
- `Goal` invariant and sent-marker reset on a term change.
- `ReminderScheduler` with mocks: fire, late-fire, expiry drop, zero-I/O idle tick, event-driven
  upsert/remove, per-fire isolation.
- `ReminderEmail` per locale.

### Integration Tests:

- REST Assured on a random port (`GoalApiTest`): field round-trip, 422/400 matrix, wire-enum guard.
- Raw-SQL CHECK coverage in `GoalPersistenceTest`.
- `ReminderSchedulerIntegrationTest`: API-created task → `fireDue` → stamped + one mail, with
  proposals/rhythm/memory untouched.

### Manual Testing Steps:

1. Create a task due today with a time ~20 minutes ahead and lead `FIFTEEN_MINUTES`. Confirm one
   e-mail arrives about 15 minutes before the time.
2. Edit its time later, and confirm a second reminder arrives for the new moment.
3. Complete a task before its moment and confirm nothing arrives.
4. Switch the account to EN and confirm the next reminder is in English.

## Performance Considerations

The tick is I/O-free. DB touches are one boot query, the goal writes that already happen, and one
short transaction per fire (roughly one per dated task). The map holds all future unsent reminders,
which is trivially small at MVP scale. Neon's idle window is not affected (`lessons.md`).

**Mail allowance.** Resend allows 100 messages a day for everything, and `app.mail.auth-daily-budget=60`
reserves auth's share (`AuthMailBudget`). Reminders share the remaining ~40 with proposals (one every
2–7 days per account), and like proposal mail they are not capped. At one mail per dated task for a
handful of accounts this leaves wide headroom. If the provider ever rejects a send for quota, which
shows up as the fire's ERROR log, the next step is a cap or a digest. Neither is built now.

## Migration Notes

`V18` is expand-only: nullable columns and a CHECK that is weaker for every row an older image
creates. Existing dated tasks have `reminder_sent_at = null`, so after deploy those still before
their expiry are loaded at boot and remind normally. Tasks already past expiry are not loaded.

Rollback leaves the columns unused, with one known edge. The previous image updates only the
columns it maps, so an edit that clears the date of a **timed** task, or converts it to another
layer, leaves `due_time` set without `due_date`. The CHECK rejects that, and the edit fails with a
500 until roll-forward. The old image also changes a date without clearing `reminder_sent_at`. This
is accepted for a single-user MVP, since a rollback is short-lived. If a rollback has to stay in
place, `UPDATE goal SET due_time = NULL, reminder_lead = NULL` restores full old-image behaviour.
That statement is data-destructive for times only, never for dates.

## References

- Frame brief: `context/changes/due-date-reminder/frame.md`
- Pattern: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalScheduler.java`
- Zone and DST handling: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalRhythm.java`
- Event precedent: `backend/src/main/java/com/thedariusz/todoai/goal/GoalLeftTheRunning.java`
- Per-locale mail: `backend/src/main/java/com/thedariusz/todoai/auth/VerificationEmailPl.java`
- Wire guard: `backend/src/test/java/com/thedariusz/todoai/GoalApiTest.java:599`
- Lessons: `context/foundation/lessons.md` (scale-to-zero DB; contract duplicated across the stack;
  "Delivered" is not delivered)
- Linear: DEV-63, branch `thedariusz/dev-63-zadanie-na-konkretna-godzine-i`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Term Model and Contract

#### Automated

- [x] 1.1 Backend suite passes, including the new API/persistence cases and the widened wire-enum guard — cab1210
- [x] 1.2 Flyway applies V18 and Hibernate ddl-auto=validate boots — cab1210
- [x] 1.3 Frontend typechecks and builds with the extended type — cab1210
- [x] 1.4 Frontend lint and tests stay green — cab1210

### Phase 2: Reminder Scheduler and Mail

#### Automated

- [x] 2.1 Backend suite passes, including all new reminder tests — 3d455d7
- [x] 2.2 Reminder isolation from proposals holds (no proposal, rhythm, memory or goal.updated_at write on fire) — 3d455d7
- [x] 2.3 Liveness group includes the reminder pulse — 3d455d7

#### Manual

- [ ] 2.4 A reminder for a timed task arrives on the phone in the inbox at the expected moment, in its account language

### Phase 3: SPA Form and Chip

#### Automated

- [ ] 3.1 Frontend tests pass
- [ ] 3.2 Lint and typecheck/build pass
- [ ] 3.3 Full gate green
- [ ] 3.4 Form works end to end at 375px with clean console

#### Manual

- [ ] 3.5 The time and lead controls feel right on a phone, in both locales

### Phase 4: PRD and Living Documentation

#### Automated

- [ ] 4.1 Docs structure test passes
- [ ] 4.2 Mermaid diagrams render
- [ ] 4.3 Every one of the nine surfaces mentions the change where applicable
- [ ] 4.4 Full gate green

#### Manual

- [ ] 4.5 The ownership line reads as consistent with the natural-rhythm principle

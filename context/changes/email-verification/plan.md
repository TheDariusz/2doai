# Email Verification at Sign-up — Implementation Plan

## Overview

Registration stays open, but an account is inert until its owner enters a 6-digit code
e-mailed to the address: it cannot log in, and the natural rhythm never e-mails it. This closes
the one live abuse path found in the 2026-09-08 production risk review — the app as a delayed
spam relay to addresses nobody consented with — without introducing an invite list. Code
e-mails are throttled per address in memory so a bot cannot burn the Resend daily quota that
real proposal e-mails depend on.

## Current State Analysis

- **Registration is a single transaction with no address proof.** `RegistrationService.register`
  (`backend/src/main/java/com/thedariusz/todoai/auth/RegistrationService.java`) encodes the
  password, `saveAndFlush`es the `User`, saves the `AiMemory` root and publishes `UserRegistered`
  inside the transaction. `UserController.register` returns 201 with `Location: /api/users/me`.
- **The scheduler adopts an account the moment it registers.** `ProposalScheduler.scheduleNewAccount`
  listens to `UserRegistered`; `loadSchedule` calls `users.findAll()` at boot and schedules every
  row. Both paths end in a Sonnet call and an e-mail to the stored address
  (`proposal/ProposalScheduler.java`, `fire`).
- **Login has the right hook, hardcoded open.** `UserPrincipal.isEnabled()` returns `true`;
  `AppUserDetailsService` loads by normalized email; the auto-configured
  `DaoAuthenticationProvider` runs `AccountStatusUserDetailsChecker` **before** the password
  check, so a disabled account would be reported as disabled even on a wrong password.
- **Failures already speak Problem JSON with a `type` URN.** `ApiExceptionHandler` maps
  `ReAuthenticationFailedException` to 403 + `urn:2doai:problem:re-auth-failed`;
  `ProblemDetailsSecurityHandler.commence` maps `AuthenticationException` to 401 (503 for
  `AuthenticationServiceException`). The SPA discriminates on `ApiError.type`, never on `detail`.
- **`User` has no setters by rule.** Every mutation is a targeted `@Modifying` update on
  `UserRepository` (`scheduleNextProposalAt`, `updateLanguage`) because a `save` of a detached
  row can resurrect a deleted account. The javadoc on both methods is the contract new writes
  must follow.
- **Mail is a port with one adapter and one caller.** `mail/EmailSender.send(to, subject, text)`
  → `SmtpEmailSender` (Resend over SMTP, 10 s timeouts). `ProposalEmail` is Polish-only (the
  accepted FR-009 exception); `ProposalTemplatePl`/`En` are the localized pair to mirror for any
  new user-facing text. Resend free plan: 100 e-mails/day, 3 000/month (runbook Phase 8).
- **Tests hit a real server and a real Postgres.** `ApiTestBase` (REST Assured, cookies + CSRF)
  offers `register`, `login`, `givenLoggedInUser`; `TestcontainersConfiguration` is imported by
  every `@SpringBootTest`. `ProposalSchedulerIntegrationTest` stubs `EmailSender` with
  `@MockitoBean`; `ProposalSchedulerTest.schedulesAFreshAccountWithoutWaitingForARestart` pins
  the `UserRegistered` listener.
- **Frontend.** `AuthPage` (one component for `/login` and `/register`) calls
  `useAuth().register` then navigates to `/login`; `messageFor` maps statuses to i18n keys;
  `AuthProvider` implements the `Auth` contract from `auth-context.ts`; routes live in
  `App.tsx`; `test/auth.tsx` provides `stubAuth` for screen tests.
- **Latest migration is `V12`**; `PasswordEncoder` is the delegating BCrypt bean in `SecurityConfig`.
- **Rules from `context/foundation/lessons.md` that bind this plan:** nothing may query Neon on a
  schedule shorter than ~5 min (so no periodic cleanup of dead sign-ups); a wire literal copied
  across Java/YAML/TS needs one guard that reads `openapi.yaml` and checks both sides (the
  re-auth URN guard in `AuthApiTest` is the template).

## Desired End State

1. `POST /api/users` creates an **unverified** account and e-mails a 6-digit code (15-minute
   TTL) in the account's language. Registering an address that already has an unverified
   account replaces its password and issues a fresh code instead of answering 409.
2. `POST /api/verifications {email, code}` → 204 and the account is verified; wrong, expired,
   exhausted (5 failed attempts) or unknown → 403 `urn:2doai:problem:verification-failed`.
3. `POST /api/verification-codes {email}` → 202 always; more often than once per 60 s or more
   than 5 per hour per address → 429 with `Retry-After`.
4. `POST /api/sessions` for an unverified account with the **correct** password → 403
   `urn:2doai:problem:email-not-verified`; with a wrong password → the generic 401, unchanged.
5. The natural rhythm schedules an account on `UserVerified`, never before; boot skips
   unverified rows. No e-mail other than the code ever reaches an unverified address.
6. Existing accounts are verified by the migration backfill and notice nothing.
7. SPA: sign-up leads to `/verify`; entering the code leads to `/login` with a confirmation;
   logging in unverified leads to `/verify`; "send again" works and reports the cooldown.

Verify by: `/check` green; the new `AuthApiTest` cases; a manual sign-up on production with a
real mailbox (see Phase 4).

### Key Discoveries:

- `DaoAuthenticationProvider` pre-authentication checks run `isEnabled()` before the password —
  the disabled check must move to **post**-authentication or the URN leaks on wrong passwords.
- `UserRepository` targeted-update rule: verification state must be written with `@Modifying`
  queries, not through setters on `User`.
- Sending SMTP inside the registration transaction would pin a Hikari connection for up to the
  10 s SMTP timeouts — the send goes **after** commit, and a failed send leaves an unverified row
  that the next registration or resend simply overwrites.
- Only `ProposalSchedulerIntegrationTest` mocks `EmailSender`; a `@Primary` recording fake in
  `TestcontainersConfiguration` would give `@MockitoBean` two candidates — that test switches to
  the fake.
- `PasswordEncoder` already exists and is slow by design — the right hash for a 6-digit code with
  no new dependency.

## What We're NOT Doing

- Per-IP rate limiting (Cloudflare rule) and the Pages→Fly shared secret — the next issue.
- Invite codes, magic links, auto-login after verification, changing an address post-signup.
- A scheduled cleanup of dead unverified rows (lessons.md: no periodic DB touch); overwrite on
  re-registration covers the blocking case.
- A daily global cap on code e-mails; a per-account LLM call cap.
- Exposing verification state on `GET /api/users/me` — an unverified account never has a session.
- Localizing `ProposalEmail` (still the FR-009 exception).

## Implementation Approach

Extend the `User` aggregate with verification state and a `UserVerified` event; make
`isEnabled()` mean "address confirmed"; add a small `auth` service that issues and checks codes
through the existing `PasswordEncoder` and `EmailSender`; keep all writes as targeted updates.
Two new public resources, both CSRF-protected like registration. Frontend adds one screen and
two context actions. Backend first, contract in the same commit as the Java, SPA last.

## Critical Implementation Details

- **Timing & lifecycle.** Define the `DaoAuthenticationProvider` bean explicitly in
  `SecurityConfig` with `setPreAuthenticationChecks(user -> {})` and
  `setPostAuthenticationChecks(new AccountStatusUserDetailsChecker())`, so `DisabledException`
  is raised only after BCrypt confirmed the password. `ProblemDetailsSecurityHandler.commence`
  then maps `DisabledException` to 403 + `urn:2doai:problem:email-not-verified`. Pinned by the
  "wrong password on an unverified account is a generic 401" test.
- **State sequencing.** Registration: commit the row (existing transaction), then issue the code
  in its own targeted update and send. A `MailDeliveryException` on that send answers 503 and
  leaves the unverified row; re-registration or `verification-codes` overwrites it. Verification:
  compare, then either `markEmailVerified` + publish `UserVerified` in one transaction, or
  increment `verification_attempts` — the failed-attempt write must land even though the
  request answers 403.
- **Code handling.** `SecureRandom` → `%06d`; store `passwordEncoder.encode(code)`; never log
  the code or the full address — mirror `SmtpEmailSender.describe` (domain + subject length).
- **Throttle memory.** The per-address map lives in the JVM (one machine, like the scheduler's
  map) and must prune expired entries on every call; an unbounded map keyed on attacker-chosen
  strings is the OOM lever on a 512 MB machine. `ponytail:` in-memory, per-address; a per-IP
  limit is the next issue.
- **Backfill is load-bearing.** `V13` must set `email_verified_at = created_at` for every
  existing row, or the deploy locks out every real user.

## Phase 1: Domain and data

### Overview

Give the aggregate a verification state the rest of the app can read, move the scheduler onto
"verified", and prove it against a real Postgres — no HTTP yet.

### Changes Required:

#### 1. Migration

**File**: `backend/src/main/resources/db/migration/V13__email_verification.sql`

**Intent**: Add the verification columns to `app_user` and mark every existing account verified.

**Contract**: `email_verified_at TIMESTAMPTZ NULL`, `verification_code_hash VARCHAR(255) NULL`,
`verification_expires_at TIMESTAMPTZ NULL`, `verification_attempts INTEGER NOT NULL DEFAULT 0`;
then `UPDATE app_user SET email_verified_at = created_at WHERE email_verified_at IS NULL`.
Expand-only; the previous image ignores the columns. Comment block in the V9/V10 style.

#### 2. Aggregate

**File**: `backend/src/main/java/com/thedariusz/todoai/user/User.java`

**Intent**: Map the four columns read-only (getters, no setters — the class rule), and expose
`isEmailVerified()`.

**Contract**: `getEmailVerifiedAt()`, `getVerificationCodeHash()`, `getVerificationExpiresAt()`,
`getVerificationAttempts()`, `isEmailVerified()`. A freshly constructed `User` is unverified
with no code.

#### 3. Repository writes

**File**: `backend/src/main/java/com/thedariusz/todoai/user/UserRepository.java`

**Intent**: The only way verification state moves — targeted updates that cannot resurrect a
deleted row, each returning the affected count.

**Contract**: `issueVerificationCode(id, codeHash, expiresAt, now)` (also resets attempts to 0);
`recordFailedVerificationAttempt(id, now)`; `markEmailVerified(id, now)` (clears hash/expiry);
`replaceUnverifiedAccount(id, passwordHash, language, now)` for the re-registration path —
guarded by `email_verified_at IS NULL` in the JPQL so it can never touch a verified account.
`updated_at` set in each, as the existing two do.

#### 4. Event

**File**: `backend/src/main/java/com/thedariusz/todoai/user/UserVerified.java` (replaces
`UserRegistered.java`)

**Intent**: The moment an account becomes one the app may act on unprompted. `UserRegistered`
has no other consumer and is deleted rather than kept beside it.

**Contract**: `record UserVerified(UUID userId)`; published inside the verifying transaction.

#### 5. Scheduler

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalScheduler.java`

**Intent**: Adopt accounts on `UserVerified`; skip unverified rows at boot.

**Contract**: `scheduleNewAccount(UserVerified)`; `loadSchedule` filters
`account.isEmailVerified()` before scheduling. Javadoc on the class ("once when an account
registers") updated to say verifies.

#### 6. Registration stops publishing

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/RegistrationService.java`

**Intent**: Remove the `UserRegistered` publish; the transaction still creates user + memory.

**Contract**: `register(...)` no longer needs `ApplicationEventPublisher`.

#### 7. Tests

**Files**: `backend/src/test/java/com/thedariusz/todoai/user/UserVerificationPersistenceTest.java`
(new, Testcontainers), `proposal/ProposalSchedulerTest.java`

**Intent**: Prove the migration + updates against Postgres (a new row is unverified; each
update moves exactly its columns and returns 0 for a missing id; `replaceUnverifiedAccount`
returns 0 for a verified row), and re-pin the scheduler on `UserVerified` plus "boot skips an
unverified account".

### Success Criteria:

#### Automated Verification:

- `mvn test -Dtest=UserVerificationPersistenceTest` passes (Flyway applies V13 on a fresh container)
- `mvn test -Dtest=ProposalSchedulerTest` passes with the `UserVerified` listener and the boot filter
- `mvn test` green — `UserOwnedConventionTest`, `AccountDeletionIntegrationTest` unaffected

#### Manual Verification:

- None — no user-visible surface yet.

---

## Phase 2: API — issue, verify, resend, gate login

### Overview

The wire surface: registration sends a code, two new public resources, the login gate, the
contract, and the test harness that lets every suite obtain a code without SMTP.

### Changes Required:

#### 1. Verification service

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/EmailVerificationService.java`

**Intent**: Issue a code (registration and resend), check a submitted code, and own the
constants: 6 digits, 15-minute TTL, 5 attempts.

**Contract**: `issue(User)` — targeted update, then `EmailSender.send` after the update commits;
throws `MailDeliveryException` through. `verify(Email, String code)` — loads by email; unknown,
verified-already, expired, exhausted or mismatching → `VerificationFailedException` (one type,
fixed detail; the failed attempt is recorded before throwing); match → `markEmailVerified` and
publish `UserVerified` in one transaction. Uses the existing `PasswordEncoder` for hash/match.

#### 2. Throttle

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/VerificationThrottle.java`

**Intent**: Per-address cooldown (60 s) and hourly cap (5) for code e-mails, in memory.

**Contract**: `admit(String email, Instant now)` returns the seconds to wait (0 = allowed) and
records the send; prunes entries older than one hour on every call. Consulted by both the
registration path and `verification-codes`.

#### 3. Localized code e-mail

**Files**: `backend/src/main/java/com/thedariusz/todoai/auth/VerificationEmailPl.java`,
`VerificationEmailEn.java`

**Intent**: Subject and plain-text body in the account's language — the text the user reads,
so one implementation per locale (the `ProposalTemplatePl/En` rule).

**Contract**: `subject(code)`, `body(code)`; body names the 15-minute validity; no link.

#### 4. Registration path

**Files**: `backend/src/main/java/com/thedariusz/todoai/auth/RegistrationService.java`,
`user/UserController.java`

**Intent**: On the email-unique violation, if the existing row is unverified, replace its
password + language and fall through to issuing a fresh code; only a verified duplicate is 409.
After the transactional create/replace returns, the controller (or a non-transactional wrapper)
calls `issue`. Throttle applies before sending.

**Contract**: 201 unchanged for a new address; 201 again for an overwritten unverified one;
409 for a verified duplicate; 429 when throttled; 503 when the code could not be sent.

#### 5. Endpoints

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/VerificationController.java`

**Intent**: `POST /api/verifications {email, code}` → 204; `POST /api/verification-codes {email}`
→ 202 (also for an unknown or already-verified address — no enumeration, no e-mail sent).

**Contract**: Request records `EmailVerification(email, code)` with `@NotBlank @Pattern("\\d{6}")`
and `VerificationCodeRequest(email)`; both public in `SecurityConfig`, CSRF applies.

#### 6. Failure mapping

**Files**: `backend/src/main/java/com/thedariusz/todoai/auth/ApiExceptionHandler.java`,
`security/ProblemDetailsSecurityHandler.java`, `security/SecurityConfig.java`,
`security/UserPrincipal.java`

**Intent**: `VerificationFailedException` → 403 + `urn:2doai:problem:verification-failed`;
`VerificationThrottledException` → 429 + `Retry-After`; `MailDeliveryException` → 503;
`DisabledException` → 403 + `urn:2doai:problem:email-not-verified`. `UserPrincipal` carries a
`verified` boolean feeding `isEnabled()`; the explicit `DaoAuthenticationProvider` bean moves
the disabled check post-authentication (see Critical Implementation Details).

#### 7. Contract

**File**: `context/foundation/openapi.yaml`

**Intent**: Same commit as the Java. New paths `/verifications` and `/verification-codes`; new
schemas `EmailVerification`, `VerificationCodeRequest`; new response `TooManyRequests`; `/users`
POST gains 429 and 503 and a description of the code; `/sessions` POST 403 documents the
`email-not-verified` URN alongside CSRF exactly as `/users/me` DELETE documents re-auth.

#### 8. Test harness and cases

**Files**: `backend/src/test/java/com/thedariusz/todoai/TestcontainersConfiguration.java`,
`ApiTestBase.java`, `AuthApiTest.java`, `VerificationApiTest.java` (new),
`proposal/ProposalSchedulerIntegrationTest.java`

**Intent**: A recording `EmailSender` fake (`@Bean @Primary`) that keeps the last code per
address; `ApiTestBase.register` verifies through the real endpoint using it, so every existing
suite keeps working unchanged; `ProposalSchedulerIntegrationTest` drops `@MockitoBean` for the
fake. Cases: code sent at registration; unverified + right password → 403 URN; unverified +
wrong password → 401; wrong code → 403; 5 wrong then right → 403; expired (expiry moved back via
`JdbcTemplate`) → 403; overwrite of an unverified duplicate (old password rejected, new accepted
after verify); verified duplicate → 409; resend cooldown → 429 with `Retry-After`; resend for an
unknown address → 202 and no e-mail; **URN guard**: both new URNs extracted from real responses
and asserted present in `openapi.yaml` and `frontend/src/auth/problems.ts` (lessons.md).

### Success Criteria:

#### Automated Verification:

- `mvn test -Dtest=AuthApiTest,VerificationApiTest` passes every case above
- `mvn test` green (the fake replaces SMTP everywhere; no suite reaches smtp.resend.com)
- `node --test docs/index.test.mjs` still green (touched only in Phase 4)

#### Manual Verification:

- `OPENROUTER_API_KEY= RESEND_API_KEY=… mvn spring-boot:run` locally: register with a real
  mailbox, receive the code, verify with curl, log in — and confirm the scheduler log line
  "Natural rhythm loaded" counts only verified accounts.

---

## Phase 3: Frontend — the /verify screen

### Overview

The SPA half: one new screen, two new context actions, the two URNs, copy in both languages.

### Changes Required:

#### 1. Problem types

**File**: `frontend/src/auth/problems.ts` (new)

**Intent**: The SPA's single copy of the two URNs — the file the backend guard reads.

**Contract**: `export const EMAIL_NOT_VERIFIED = 'urn:2doai:problem:email-not-verified'`,
`VERIFICATION_FAILED = 'urn:2doai:problem:verification-failed'`.

#### 2. Auth contract and provider

**Files**: `frontend/src/auth/auth-context.ts`, `AuthProvider.tsx`, `test/auth.tsx`

**Intent**: `verify(email, code)` → `POST /verifications`; `resendCode(email)` →
`POST /verification-codes`; `stubAuth` gains both.

#### 3. Verify screen and routing

**Files**: `frontend/src/pages/VerifyPage.tsx` (new), `App.tsx`

**Intent**: `/verify` reads the address from `location.state` (editable input fallback so a
reload still works), a 6-digit input (`inputMode="numeric"`, `autoComplete="one-time-code"`,
`pattern="\d{6}"`), submit → `verify` → `navigate('/login', { state: { verified: true } })`;
"Send again" → `resendCode`, 429 shown as "wait a minute". Public route beside `/login`.

#### 4. Auth page transitions

**File**: `frontend/src/pages/AuthPage.tsx`

**Intent**: Register success → `/verify` with the email; login 403 with `EMAIL_NOT_VERIFIED` →
`/verify` with the email and a hint; `/login` shows the "address confirmed, sign in" notice
when `state.verified`; `messageFor` handles 429 and 503 on registration.

#### 5. Copy

**Files**: `frontend/src/i18n/pl.ts`, `en.ts`

**Intent**: Keys for the screen heading, code label, hint ("we sent a code to …"), send-again,
verified notice, and the errors: wrong/expired code, not verified, too many requests, mail
unavailable.

#### 6. Tests

**Files**: `frontend/src/pages/VerifyPage.test.tsx` (new), `AuthPage.test.tsx`,
`auth/AuthProvider.test.tsx`

**Intent**: Screen submits the code and lands on `/login` with the notice; wrong code shows
the error; resend shows the cooldown on 429; registration navigates to `/verify` with the
address; unverified login navigates to `/verify`; provider posts the two bodies.

### Success Criteria:

#### Automated Verification:

- `npm test` green including the new file
- `npm run lint` clean; `npm run build` passes `tsc`

#### Manual Verification:

- Dev servers (`.claude/launch.json` pointed at this worktree): full sign-up → e-mail → code →
  login in the browser, in PL and in EN; the unverified-login redirect; the 429 message after
  two quick "send again" clicks.

---

## Phase 4: Documentation, gate, deploy

### Overview

The slice is not done until the reviewer page and runbook describe it and production has
verified a real sign-up.

### Changes Required:

#### 1. Living documentation

**File**: `docs/index.html`

**Intent**: The full walk from CLAUDE.md, in order: `#overview` capability row for accounts
(verification), `#code-map` (no new package — `auth` row prose), `#backend` prose + class
diagram (`User` fields, `UserVerified`, `EmailVerificationService`, `VerificationThrottle`),
terms `<dl>` (verification code), `#flows` registration diagram extended with the code, login
diagram with the `email-not-verified` branch, `#data` prose for the four columns, `#roadmap` /
`#glossary` status, the endpoint list in the `#backend` note (two new paths), and the
`Verified against …` stamp with the new issue id.

#### 2. Ops docs

**Files**: `context/foundation/deployment-runbook.md`, `README.md`

**Intent**: Runbook Phase 8 note: registration now sends mail (no new secret; the Resend daily
quota now also carries sign-ups; the fake keeps the suite hermetic); Phase 8.4 smoke gains the
sign-up round trip. README "What it does" mentions the confirmed address.

#### 3. Data model diagrams

**Files**: `context/foundation/data-model.md`, `data-model-current.drawio` + `.svg`

**Intent**: Four columns on `app_user`; re-export the SVG with the light-palette fix (memory:
draw.io export writes `color-scheme: light dark`).

#### 4. Gate and handoff

**Intent**: `/check` green; Linear comment (done / next / blocked); issue to In Review; PR.

### Success Criteria:

#### Automated Verification:

- `/check` green (backend tests, frontend test + lint + build)
- `node --test docs/index.test.mjs` green

#### Manual Verification:

- Every item of the nine-step docs walk ticked against the merged code, not from memory
- After deploy: sign up on `https://2doai.app` with a real mailbox, receive the code, verify,
  log in; delete that account via the UI afterwards
- Existing accounts still log in (backfill held)
- Fly log at boot shows the scheduler counting only verified accounts

---

## Testing Strategy

### Unit Tests:

- `VerificationThrottle`: first send allowed; second within 60 s returns the wait; sixth within
  an hour refused; entries older than an hour pruned.
- `ProposalSchedulerTest`: `UserVerified` schedules; boot skips unverified.
- `VerificationEmailPl/En`: subject carries the code; body names the validity.

### Integration Tests:

- `UserVerificationPersistenceTest`: V13 columns, each targeted update, the verified-guard on
  `replaceUnverifiedAccount`.
- `AuthApiTest` / `VerificationApiTest`: the full matrix in Phase 2 §8, plus the URN guard.
- `ProposalSchedulerIntegrationTest` on the recording fake.

### Manual Testing Steps:

1. Register with a real mailbox in PL; read the code in the e-mail; enter it; log in.
2. Repeat in EN — subject/body English.
3. Register the same address again before verifying with a different password; verify; the new
   password works, the old does not.
4. Log in before verifying with the right password → sent to `/verify`; with a wrong one →
   "incorrect email or password".
5. Click "send again" twice → the cooldown message.
6. Delete the account; register again → 201, new code.

## Performance Considerations

- Registration adds one BCrypt of the code and one targeted update; verification adds one
  read + one update. Both are rare, request-scoped, and never hold a connection across SMTP.
- The throttle map is bounded by pruning; no scheduled work, no DB touch — Neon idleness holds.
- `loadSchedule` still reads all rows once per boot; the filter is in Java.

## Migration Notes

- `V13` is expand-only with a backfill; a rollback to the previous image leaves the columns
  unread and every account behaves as verified — acceptable, since the previous image never
  sends a code either.
- Accounts created under the new image and never verified would become live under a rolled-back
  image; if a rollback ever happens, delete unverified rows by hand first.

## References

- Risk review that opened this change: session of 2026-09-08 (invite code rejected in favour of
  address proof; per-IP limits deferred to the next issue)
- Pattern for URN failures: `backend/src/main/java/com/thedariusz/todoai/auth/ApiExceptionHandler.java`
- Pattern for targeted updates: `backend/src/main/java/com/thedariusz/todoai/user/UserRepository.java`
- Pattern for localized user text: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalTemplatePl.java`
- Contract guard template: `AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode`
- Rules: `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Domain and data

#### Automated

- [x] 1.1 `mvn test -Dtest=UserVerificationPersistenceTest` passes (Flyway applies V13 on a fresh container) — b1fea03
- [x] 1.2 `mvn test -Dtest=ProposalSchedulerTest` passes with the `UserVerified` listener and the boot filter — b1fea03
- [x] 1.3 `mvn test` green — `UserOwnedConventionTest`, `AccountDeletionIntegrationTest` unaffected — b1fea03

### Phase 2: API — issue, verify, resend, gate login

#### Automated

- [x] 2.1 `mvn test -Dtest=AuthApiTest,VerificationApiTest` passes every case above
- [x] 2.2 `mvn test` green (the fake replaces SMTP everywhere; no suite reaches smtp.resend.com)
- [x] 2.3 `node --test docs/index.test.mjs` still green (touched only in Phase 4)

#### Manual

- [ ] 2.4 Local run: register with a real mailbox, receive the code, verify with curl, log in; scheduler counts only verified accounts

### Phase 3: Frontend — the /verify screen

#### Automated

- [ ] 3.1 `npm test` green including the new file
- [ ] 3.2 `npm run lint` clean; `npm run build` passes `tsc`

#### Manual

- [ ] 3.3 Dev servers: full sign-up → e-mail → code → login in PL and EN; unverified-login redirect; 429 message after two quick "send again" clicks

### Phase 4: Documentation, gate, deploy

#### Automated

- [ ] 4.1 `/check` green (backend tests, frontend test + lint + build)
- [ ] 4.2 `node --test docs/index.test.mjs` green

#### Manual

- [ ] 4.3 Every item of the nine-step docs walk ticked against the merged code
- [ ] 4.4 After deploy: sign up on https://2doai.app with a real mailbox, receive the code, verify, log in; delete that account afterwards
- [ ] 4.5 Existing accounts still log in (backfill held)
- [ ] 4.6 Fly log at boot shows the scheduler counting only verified accounts

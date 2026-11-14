# Passwordless Sign-in (replacing the password credential) Implementation Plan

> The change-id is `password-reset` for historical reasons. The frame brief
> (`frame.md`) established that the work is a replacement of the sign-in
> credential, not a reset feature.

## Overview

Ownership of the e-mail address is proven once at sign-up and never honoured
again, so the account hangs on one remembered secret. This plan makes an
e-mailed eight-digit code the only sign-in credential: one mail carries the code
and a link that prefills it. The password is removed from sign-up, sign-in and
account deletion. Sessions become long-lived and survive a backend restart, so
the mail round trip stays rare.

The work ships in two releases. Release 1 (Phases 1-3) adds code sign-in beside
the password and unlocks the owner's account. Release 2 (Phases 4-5) removes
the password.

## Current State Analysis

- **One credential, no recovery.** `POST /api/sessions` authenticates
  `{email, password}` through a `DaoAuthenticationProvider`
  (`session/SessionController.java:73-87`, `security/SecurityConfig.java:217-226`).
  There is no reset. `User` has no method that changes `password_hash`.
- **A hardened code already exists, but only for unverified accounts.**
  `EmailVerificationService` issues a 6-digit code (15 minutes, 5 attempts,
  BCrypt-hashed, one BCrypt per request as a timing guard). Every write is gated
  on `emailVerifiedAt is null` (`user/UserRepository.java:103-158`,
  `User.java:221-225`), `resendCode` filters out verified accounts
  (`auth/EmailVerificationService.java:134`), and a successful
  `POST /api/verifications` answers 204 without opening a session.
- **One code slot, no purpose.** Four columns on `app_user` (V13) plus a pairing
  CHECK (V14). Latest migration is V14.
- **Throttle is per address and in memory**: 60 s cooldown, 5 per hour
  (`auth/VerificationThrottle.java:50-57`). There is no global cap and no login
  rate limit.
- **Mail is synchronous and link-free.** `EmailSender.send` blocks up to ~30 s
  and a failure is a 503. Its javadoc records that the verification mail
  "deliberately carries no link at all" (`mail/EmailSender.java:12-15`). The
  public base URL is already available as `MailboxProperties.baseUrl()`.
- **Sessions are short and die on deploy.** 30-minute idle timeout,
  browser-session cookie, no remember-me, in memory on the single Fly machine
  (`application.properties:37-46`, `auth-session-model.md:48`).
- **Account deletion re-authenticates with the password**, compared against the
  hash carried in `UserPrincipal` (`user/UserController.java:149-173`). A wrong
  password is 403 `urn:2doai:problem:re-auth-failed`, guarded across Java,
  `openapi.yaml` and `frontend/src/auth/problems.ts` by
  `AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode`.
- **Registration answers 409 for any known address**, an accepted existence leak
  that `docs/index.html:564` lists as waiting on a recovery path.
- **Frontend.** `AuthPage.tsx` serves `/login` and `/register` with e-mail and
  password fields. `VerifyPage.tsx` takes the address from router state, has one
  code field, no resend cooldown, and returns to `/login` after success. No
  route reads anything from the URL. `AccountMenu.tsx` confirms deletion with a
  password field. The API client exposes status and Problem `type` only
  (`api/client.ts:18-29`).
- **Tests.** Most backend suites sign in through
  `ApiTestBase.givenLoggedInUser()` (`ApiTestBase.java:170-175`); about 20 tests
  construct `new User(email, hash, language)` directly. Backend tests read
  `VerifyPage.tsx`, `problems.ts`, `pl.ts` and `en.ts` as cross-boundary guards.
  There is no e2e suite, by decision (`test-plan.md:147`).
- **Mail deliverability.** `outlook.com` silently discards mail from
  `2doai.app`; the Resend free plan is 100 messages a day shared with proposals
  (`lessons.md:38-44`, `deployment-runbook.md:347-351,385,413-420`).

## Desired End State

- A visitor enters an address on `/register` or `/login` and always gets the
  same answer. If a mail is due, it carries an eight-digit code and a link.
- Typing the code, or opening the link and pressing the button, opens a
  session. The first successful code also proves the address.
- A user stays signed in across browser restarts and backend deploys, until 90
  days pass without a sign-in or a use of the remember-me token.
- Account deletion is confirmed by a fresh code requested from the dialog.
- No endpoint, schema, screen or document mentions a password as a credential.
  `password_hash` is nullable and unmapped, waiting for a later drop.

Verify with `/check` green, the sign-up round trip in the runbook walked in
production on a Gmail address, and `docs/index.html` walked per the nine
surfaces in `CLAUDE.md`.

### Key Discoveries:

- `POST /api/sessions` is documented as the sole session-creation path
  (`openapi.yaml:322`). Accepting a code there keeps that invariant.
- Spring adopts a single `AuthenticationProvider` bean; a second one silently
  makes it adopt neither (`SecurityConfig.java:202-216`). Code sign-in must not
  add a provider bean.
- `markEmailVerified` is both "prove the address" and "consume the code" and
  returns 0 on a verified row (`UserRepository.java:151-158`). Sign-in needs a
  consume step that works on verified rows.
- `UserVerified` drives `user_registered` and the proposal schedule
  (`observability/RegistrationAuditLogger.java:30-36`,
  `proposal/ProposalScheduler.java:178-181`). It must fire once per account, not
  once per sign-in.
- `AccountDeletionIntegrationTest` asserts that no `user_id` foreign key has
  `ON DELETE`; any new per-user table needs a `PerUserDataDeleter`.
- Any 401 makes the SPA dispatch `session-expired` (`api/client.ts:75-80`). That
  is harmless on a sign-in attempt, which is already anonymous.
- ESLint rejects Polish diacritics in components (`eslint.config.js:9-38`); all
  copy goes to `src/i18n/pl.ts` and `en.ts`, which `tsc` keeps in step.
- The five-guess cap is not atomic today: `recordFailedVerificationAttempt` has
  no `attempts < MAX` predicate (`UserRepository.java:129-132`), so parallel
  guesses can exceed five per code.
- With six digits, 5 guesses per code and 5 codes an hour, a targeted address
  faces 600 guesses a day, roughly a 20% yearly chance of a correct guess. That
  was acceptable for a sign-up code and is not for the only credential.
- Spring Security is 7.1.2 and Tomcat 11.0.27. Verified in their sources:
  persistent remember-me rotates its token on every auto-login and raises
  `CookieTheftException` (above `ExceptionTranslationFilter`, so a 500) on a
  concurrent second request; auto-login clears the `XSRF-TOKEN` cookie; the
  session cookie's `max-age` is set only at session creation.
- The `\d{6}` guards in `VerificationApiTest.java:484-495` read
  `VerifyPage.tsx` and `openapi.yaml`, so the code length cannot change on one
  side alone.

## What We're NOT Doing

- No second credential: no recovery codes, no passkeys, no OAuth.
- No per-IP rate limit at the Cloudflare edge and no Pages-to-Fly shared secret.
  **Accepted risk:** the throttle is keyed by address, so anyone who knows an
  address can spend its five codes an hour and overwrite the owner's pending
  code, blocking that person's sign-in while the attack runs. The per-IP edge
  limit is the named follow-up change that closes this.
- No separate high-entropy link token and no sign-in on page load. The link
  only prefills the code.
- No sessions in Postgres. The remember-me token is not a session store.
- No scheduled cleanup job of any kind (Neon idleness rule, `lessons.md:12-17`).
- No `DROP COLUMN password_hash`. That is a later, separate change.
- No e2e suite, no service worker, no change of e-mail provider.
- No address change, no multi-address accounts, no HTML mail.
- No manual repair of the owner's row in production.

## Implementation Approach

Reuse the existing code machinery instead of building a parallel one: lift the
"unverified only" gates, add a purpose to the single slot, and let the first
redeemed code prove the address. Keep `POST /api/sessions` as the only place a
session is born. Follow expand/contract so an image rollback stays safe:
release 1 teaches the schema and the running image to tolerate an account
without a password before release 2 creates one.

Each phase is TDD (failing test first) and carries its own `openapi.yaml` and
`docs/index.html` updates. A phase is one PR.

## Critical Implementation Details

### Timing & lifecycle

- **Uniform answers need uniform timing.** A 202 for every address is worthless
  if a known address takes seconds (SMTP) and an unknown one returns at once.
  Everything after the throttle and budget checks (address lookup, account
  creation in Phase 4, code issue, SMTP) runs as one task on a named auth-mail
  executor, so the request thread does identical work for every address. Tests
  replace that executor with a synchronous one, because about 40 call sites read
  the mailed code immediately after the response (`ApiTestBase.java:134`). Throttle and daily budget are consulted before
  the address lookup, as the throttle already is.
- **Asynchronous sending removes the 503 signal** on the code-request
  endpoints. A failed send must refund the throttle and the budget and log a
  warning; the user-visible recovery is the resend button.
- **Remember-me must not rotate its token.** Spring's
  `PersistentTokenBasedRememberMeServices` rotates on every auto-login; two
  requests with the same cookie (two restored tabs, or StrictMode's double
  bootstrap in dev) then delete all of the user's tokens and answer 500. Phase 3
  uses services that validate series and value without rewriting the value.
- **Remember-me auto-login clears the CSRF cookie.** `CsrfAuthenticationStrategy`
  runs after `CsrfCookieFilter` has already written its cookie, so the response
  must re-materialise the token, as `SessionController.materializeRotatedCsrfToken`
  does for login. Otherwise the first mutation after a deploy fails in
  `client.ts` before it is sent.
- **Remember-me wiring facts (Spring Security 7.1.2).** The configurer already
  saves the context to the `HttpSession` and registers the session. The services
  must be a declared bean with `alwaysRemember`, because a JSON body is not a
  request parameter. `SameSite` needs `setCookieCustomizer`, and the cancel
  cookie ignores it. The app's `DELETE` endpoints use `apiLogoutHandler`, so the
  services are added to it by hand. The token repository contract is keyed by
  username (the e-mail), so a `user_id` table needs a mapping.
- **Session cookie lifetime is fixed at creation.** A `max-age` on `JSESSIONID`
  would expire 30 days after sign-in regardless of activity. The session cookie
  therefore stays a browser-session cookie and the remember-me cookie carries
  persistence.

### State sequencing

- **Expand before contract.** V15 makes `password_hash` nullable and release 1's
  `User`, `UserPrincipal` and password login must already tolerate a null hash,
  even though release 1 never writes one. Release 2 then creates such rows, and
  a rollback to the release 1 image still boots and still signs them in by code.
- **One slot, two purposes.** Requesting a deletion code overwrites a pending
  sign-in code. That is acceptable because the requester already holds a
  session. A code must be refused when redeemed for the other purpose.

### Debug & observability

- The link's path and fragment parameter names are a contract between the Java
  mail builder and `VerifyPage.tsx`. Per `lessons.md:29-36` it needs one guard
  that reads both sides, in the style of the existing `VerificationApiTest`
  file guards.
- Never log a code, a link, or a full address. The existing `@domain` logging
  convention applies to every new log line.

## Phase 1: Code sign-in, backend (release 1)

### Overview

Verified accounts can request a code and exchange it for a session. The
password path keeps working. The mail gains the link, auth mail moves off the
request thread on the code-request endpoint, and a daily budget protects the
Resend quota.

### Changes Required:

#### 1. Schema expand

**File**: `backend/src/main/resources/db/migration/V15__passwordless_sign_in.sql`

**Intent**: Let an account exist without a password and let the code slot say
what it is for, without breaking the image currently in production.

**Contract**: `app_user.password_hash` drops `NOT NULL`. New column
`app_user.verification_purpose VARCHAR(32) NOT NULL DEFAULT 'SIGN_IN'`. No new
CHECK that the previous image's writes could violate.

#### 2. User aggregate and repository

**File**: `backend/.../user/User.java`, `user/UserRepository.java`, new
`user/CodePurpose.java`

**Intent**: Make the code slot usable by verified accounts and purpose-aware,
and split "consume the code" from "prove the address".

**Contract**: Enum `CodePurpose { SIGN_IN, ACCOUNT_DELETION }`.
`issueVerificationCode` takes a purpose and loses the `emailVerifiedAt is null`
clause, as does `recordFailedVerificationAttempt`. That increment becomes
atomic: it applies only while attempts are below the maximum, and zero rows
updated is a refusal. A new consuming write clears
the slot and sets `emailVerifiedAt` only when it is null, reporting whether this
was the first proof. `User.hasCodeAwaiting(now, purpose)` no longer requires an
unverified account. `passwordHash` loses `@NotBlank` and `nullable = false`; the
constructor accepts a null hash.

#### 3. Code redemption

**File**: `backend/.../auth/EmailVerificationService.java`

**Intent**: One operation that checks a code for a purpose and consumes it,
shared by sign-in now and deletion later, keeping every existing guard.

**Contract**: `CODE_LENGTH` becomes 8. `redeem(email, code, purpose)` returns
the account or throws `VerificationFailedException`; it keeps
`noRollbackFor = VerificationFailedException.class`, and callers translate that
exception outside the transaction so the wrong-guess increment commits.
`UserVerified` is published inside the transaction, because
`RegistrationAuditLogger` listens `AFTER_COMMIT`. One BCrypt comparison per call (dummy hash when
nothing is tryable). A failed attempt commits its increment. `UserVerified` is
published only when the redemption was the first proof. `resendCode` no longer
filters out verified accounts and issues a `SIGN_IN` code. The existing
`verify` (behind `POST /api/verifications`) delegates to `redeem` and still
answers 204 with no session.

#### 4. Sessions accept a code

**File**: `backend/.../session/SessionController.java`,
`auth/LoginRequest.java`, `security/UserPrincipal.java`

**Intent**: Open a session from a redeemed code through the same session
machinery the password path uses.

**Contract**: `LoginRequest(email, password?, code?)`; exactly one of `password`
and `code` must be present, otherwise 422. The code path calls `redeem`, builds
an authenticated token for `UserPrincipal.from(user)`, then runs the existing
`sessionAuthenticationStrategy`, CSRF materialisation and
`AuthenticatedSession.establish`. Every code failure is rethrown as an
`AuthenticationException` and produces the generic 401 body that bad credentials
produce today. A database failure during a code sign-in is still 503, never 401. No new `AuthenticationProvider` bean.
`UserPrincipal.passwordHash` becomes nullable; a password login against a null
hash is a generic 401.

#### 5. Mail: link, asynchronous dispatch, daily budget

**File**: `backend/.../auth/VerificationEmailPl.java`, `VerificationEmailEn.java`,
`auth/EmailVerificationService.java`, `mail/EmailSender.java` (javadoc), new
`auth/AuthMailBudget.java`, `mail/MailConfig.java`, `application.properties`

**Intent**: Put the prefilled link in the mail, stop mail latency from
revealing which addresses exist, and stop auth mail from starving proposals.

**Contract**: The body carries the code and
`{base-url}/verify#email=<url-encoded address>&code=<code>`; the subject still
carries the code (the test fake reads it there). Copy no longer assumes a
sign-up. `POST /api/verification-codes` checks throttle and budget, then
hands lookup, code issue and send to one task on a named, injectable executor
bean (bounded queue, one worker) and answers 202. The test configuration
supplies a synchronous executor under the same bean name. `AuthMailBudget` is an in-memory
daily counter sized by `app.mail.auth-daily-budget` (default 60), checked before
the address lookup; when exhausted the endpoint answers 503 for every address
and logs `event=auth_mail_budget_exhausted` once per day. A failed send refunds
throttle and budget. `POST /api/users` stays synchronous in this release. The
`EmailSender` javadoc is rewritten to record why a link is now carried and why
it is inert on GET.

#### 6. Code length across the boundary

**File**: `frontend/src/pages/VerifyPage.tsx`, `frontend/src/i18n/pl.ts`,
`frontend/src/i18n/en.ts`

**Intent**: Keep the code field and copy in step with the eight-digit code in
the same commit, because backend guards read these files.

**Contract**: `pattern="\d{8}"` and `maxLength={8}` on the code field; the hint
copy names eight digits. Nothing else on the screen changes in this phase.

#### 7. Contract and living documentation

**File**: `context/foundation/openapi.yaml`, `docs/index.html`

**Intent**: Publish the dual-credential state truthfully.

**Contract**: Spec version 1.9.0. `Credentials` allows `code` (`^\d{8}$`) as an
alternative to `password`; `EmailVerification.code` moves to the same pattern. `/verification-codes` describes sign-in for verified
accounts and the 503 budget answer. `docs/index.html` walked on all nine
surfaces: user + auth prose and class diagram, the login and "proving the
address" flows, the decision register rows for the throttle and the no-link
mail, the `Verification code` term, and the `Verified against` stamp.

### Success Criteria:

#### Automated Verification:

- Flyway applies V15 on a database at V14 and Hibernate validation passes: `mvn test -Dtest=UserRepositoryTest`
- A verified account signs in with a mailed code and receives a rotated session and CSRF token: `mvn test -Dtest=AuthApiTest`
- Wrong, expired, spent and sixth-attempt codes all return the same generic 401 on `POST /api/sessions`
- A request with both or neither of `password` and `code` returns 422
- An unverified account's first code sign-in proves the address and logs `user_registered` exactly once: `mvn test -Dtest=VerificationApiTest`
- A code request for a verified address now sends mail; an unknown address still sends none and answers 202
- The budget answers 503 for every address once exhausted and refunds on a failed send
- Parallel wrong guesses never exceed five per code
- A mailed code has eight digits, and the length guards on `VerifyPage.tsx` and `openapi.yaml` pass
- A guard test fails if the mail's link path or fragment names differ from `VerifyPage.tsx`
- `ApiSurfaceTest` passes against `openapi.yaml` 1.9.0
- Password sign-in, registration and deletion tests still pass unchanged: `mvn test`
- `node --test docs/index.test.mjs` passes

#### Manual Verification:

- Local run: request a code for a verified account, read the mail in the log or Resend, sign in with `curl` or the existing `/verify` flow
- The mail reads correctly in Polish and English and the link has the expected shape
- `docs/index.html` opened in a browser; edited Mermaid diagrams render

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Code sign-in, frontend (release 1)

### Overview

The login screen signs in by code only; the backend keeps accepting passwords
until Phase 4, which is what keeps a rollback safe. The verify screen accepts the link, opens the session and shows a resend cooldown.
Deploying this phase unlocks the owner's account.

### Changes Required:

#### 1. Auth context and API client

**File**: `frontend/src/auth/auth-context.ts`, `auth/AuthProvider.tsx`,
`api/client.ts`

**Intent**: Give screens a code sign-in call and the server's retry hint.

**Contract**: `Auth` gains `signInWithCode(email, code)` (POST `/sessions`,
adopts the returned user). `resendCode` is reused to request a sign-in code. `login(email, password)` and
`verify` are removed, since no screen calls them any more.
`ApiError` gains an optional `retryAfter` (seconds) read from `Retry-After`.

#### 2. Login screen

**File**: `frontend/src/pages/AuthPage.tsx`

**Intent**: Make the code the only way in on the screen. No password fallback
is built: the one real account cannot use its password, and the form would be
deleted again in Phase 5.

**Contract**: In `login` mode the form is the address only; submitting requests
a code and navigates to `/verify` with `state: { email, from }`. The
`EMAIL_NOT_VERIFIED` branch goes with the password form. `register` mode keeps
its password field in this release, because the backend still requires it.

#### 3. Verify screen

**File**: `frontend/src/pages/VerifyPage.tsx`, `frontend/src/App.tsx`

**Intent**: Turn the code screen into the place a session is opened, reachable
from the mail link.

**Contract**: On mount the page reads `email` and `code` from
`location.hash`, prefills both fields and removes the fragment with a history
replace. It never submits on its own. Submitting calls `signInWithCode` and
navigates to `state.from ?? '/goals'`. The resend button is disabled with a
visible countdown for 60 s after a send, or for `retryAfter` after a 429. A 401
from the sign-in maps to the existing wrong-or-expired copy. A visitor who is
already authenticated is redirected to `/goals`. The `pattern="\d{8}"` and
`maxLength={8}` attributes set in Phase 1 stay, because backend guards read them.

#### 4. Copy

**File**: `frontend/src/i18n/pl.ts`, `frontend/src/i18n/en.ts`

**Intent**: Say honestly what happens when a mail does not arrive.

**Contract**: New and changed keys for the code-first login, the hint (check
spam, valid 15 minutes, send again after a minute), and the unregistered-address
case ("if this address has an account, a code is on its way"). The 15-minute
figure stays a whole number, as the backend guard requires.

#### 5. Runbook and living documentation

**File**: `context/foundation/deployment-runbook.md`, `docs/index.html`

**Intent**: Keep the production walk and the architecture page true for
release 1.

**Contract**: Runbook section 8.4 gains the code sign-in round trip and the
control-address rule from `lessons.md:42`. `docs/index.html`: frontend prose in
`#overview`, the `frontend/src` rows of `#code-map`, the login flow, the stamp.

### Success Criteria:

#### Automated Verification:

- Login submits the address, requests a code and lands on `/verify` with the address and `from` preserved: `npx vitest run src/pages/AuthPage.test.tsx`
- `/verify` prefills from the fragment, clears it, and does not sign in without a submit: `npx vitest run src/pages/VerifyPage.test.tsx`
- A successful code returns the user to the originally requested route: `npx vitest run src/App.test.tsx`
- The resend button counts down and honours `Retry-After`
- `/login` renders no password field; `/register` still does
- `npm run lint`, `npm test` and `npm run build` pass
- Backend file guards on `VerifyPage.tsx`, `pl.ts` and `en.ts` pass: `mvn test -Dtest=VerificationApiTest`
- `/check` is green

#### Manual Verification:

- Local: sign in by typing the code; sign in by opening the link and pressing the button; the fragment is gone from the address bar afterwards
- Link opened on a phone where the PWA is installed: the code can be typed into the installed app instead
- After the production deploy, the owner signs in with a mailed code and sees their existing goals
- Control test: a code mail to a fresh Gmail address arrives; result for an `outlook.com` address is recorded in the runbook

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Long sessions that survive a restart (release 1)

### Overview

A user stays signed in across browser restarts and backend deploys for up to 90
days without activity. The database is read for this only when the in-memory
session is missing: once per browser launch or backend restart, per device.

### Changes Required:

#### 1. Schema

**File**: `backend/src/main/resources/db/migration/V16__remember_me_token.sql`

**Intent**: Store the restart-proof token.

**Contract**: Table `remember_me_token`: UUID v7 `id`, `user_id` foreign key to
`app_user` with no `ON DELETE`, unique `series`, `token_value`, `last_used_at`,
`created_at`, `updated_at`. Index on `user_id`.

#### 2. Token store

**File**: new `backend/.../security/RememberMeToken.java`,
`RememberMeTokenRepository.java`, `JpaPersistentTokenRepository.java`

**Intent**: Back Spring Security's persistent remember-me with the project's own
table and conventions, and erase tokens with the account.

**Contract**: An implementation of Spring's `PersistentTokenRepository` over the
new table. The Spring contract is keyed by username (the e-mail); the
implementation maps it to `user_id`. It also implements `PerUserDataDeleter`.
Expired rows for a user are deleted when that user signs in; there is no
scheduled cleanup.

#### 3. Security wiring and session lifetime

**File**: `backend/.../security/SecurityConfig.java`,
`session/SessionController.java`, `user/UserController.java`,
`application.properties`

**Intent**: Issue the token at every sign-in, honour it when the session is
gone, revoke it on logout and deletion, and lengthen the session.

**Contract**: `server.servlet.session.timeout=30d`; the session cookie keeps no
`max-age`. Remember-me services declared as a bean: `alwaysRemember`, 90-day
validity, and **no token rotation**. An auto-login checks series and value,
updates `last_used_at`, re-sends the cookie with the same value and a fresh
90-day lifetime, and never raises a theft error; a mismatch is an invalid
cookie, cleared, and the request continues anonymous (401). The cookie is
`HttpOnly`, `SameSite=Strict` on both the set and the cancel cookie, and
`Secure` by the same environment switch as the session cookie.
`SessionController` calls `loginSuccess` after `establish` on both sign-in
paths. The response to an auto-login carries a fresh `XSRF-TOKEN` cookie. The
services are added to `apiLogoutHandler`, so logout and account deletion clear
the cookie and delete the user's tokens. An unverified account is refused.

#### 4. Records and living documentation

**File**: `context/foundation/auth-session-model.md`,
`context/foundation/data-model.md`, `data-model-current.drawio` and its SVG
export, `context/foundation/openapi.yaml`, `docs/index.html`

**Intent**: Record why this is not "sessions in Neon" and show the new table.

**Contract**: A dated addendum to the decision record: decision 2 stands, the
token is read once per browser launch or restart per device, it does not rotate
and why, and the accepted consequence at line 48 is withdrawn. `openapi.yaml` describes the second cookie and the new
lifetime. `docs/index.html`: decision register row 553, the `Session` term and
glossary row, `#data` prose, re-exported ER SVGs (apply the light-palette fix),
security + session prose and diagram, the authenticated-request flow, the stamp.

### Success Criteria:

#### Automated Verification:

- Flyway applies V16 and Hibernate validation passes
- Signing in sets a remember-me cookie that is `HttpOnly` and `SameSite=Strict`: `mvn test -Dtest=AuthApiTest`
- A request carrying only the remember-me cookie is authenticated, and the following request with the new session cookie runs zero queries
- That auto-login response carries a fresh `XSRF-TOKEN` cookie, and a mutation sent next succeeds
- Two concurrent requests carrying the same remember-me cookie both succeed and the token survives
- A request with a live session never reads the token table
- The session cookie has no `Max-Age`; the remember-me cookie has 90 days and is renewed by an auto-login
- Logout deletes the token and clears the cookie; the old cookie no longer authenticates
- Account deletion removes the user's tokens: `mvn test -Dtest=AccountDeletionIntegrationTest`
- A forged or unknown token is refused with 401, never 500
- `mvn test` passes; `node --test docs/index.test.mjs` passes

#### Manual Verification:

- Local under `npm run dev` (StrictMode): sign in, restart the backend, reload the app, still signed in
- Production: sign in, deploy the backend, reload, still signed in, and saving a goal works without a second reload
- Neon's compute still suspends after five idle minutes with a signed-in tab left open (Neon console)
- Closing and reopening the browser keeps the user signed in

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Release 1 is complete here; let it run in production before starting Phase 4.

---

## Phase 4: Password removal, backend (release 2)

### Overview

The backend stops accepting, storing and checking passwords. Registration and
sign-in answer uniformly. Deletion is confirmed by a purpose-bound code.

### Changes Required:

#### 1. Registration

**File**: `backend/.../user/UserController.java`, `auth/RegisterRequest.java`,
`auth/RegistrationService.java`, `auth/EmailAlreadyRegisteredException.java`
(removed)

**Intent**: Sign-up asks for an address only and no longer reveals whether it is
taken.

**Contract**: `RegisterRequest(email)`. `POST /api/users` answers 202 with no
body for every valid address: a new address creates an unverified account and
mails a code; a known address mails a sign-in code. Throttle and budget are
checked before the lookup, so 429 and 503 are possible and uniform. The lookup,
the account insert, the code issue and the send all run in the executor task. The 409 and the 201 body are gone.

#### 2. Sign-in

**File**: `backend/.../auth/LoginRequest.java`, `session/SessionController.java`,
`security/SecurityConfig.java`, `security/UserPrincipal.java`,
`security/ProblemDetailsSecurityHandler.java`

**Intent**: The code is the only credential.

**Contract**: `LoginRequest(email, code)`, both required. The
`DaoAuthenticationProvider`, the `AuthenticationManager` bean and the
`DisabledException` branch are removed. The 503 for a database failure at
sign-in, which the provider supplied through `AuthenticationServiceException`,
is kept by the controller path. `AppUserDetailsService` stays, because
remember-me loads users through it. `UserPrincipal` loses `passwordHash`.

#### 3. Address verification endpoint

**File**: `backend/.../auth/VerificationController.java`,
`auth/EmailVerificationRequest.java`, `auth/ApiExceptionHandler.java`

**Intent**: Remove the path that proves an address without opening a session,
since the first sign-in now does both.

**Contract**: `POST /api/verifications` and its request record are removed.
`POST /api/verification-codes` stays as the code-request endpoint. The
`verification-failed` and `email-not-verified` URNs are removed from the Java
side.

#### 4. Account deletion

**File**: `backend/.../user/UserController.java`,
`auth/DeleteAccountRequest.java`, `auth/EmailVerificationService.java`

**Intent**: Confirm the irreversible deletion with the same proof as sign-in.

**Contract**: New `POST /api/users/me/deletion-codes` (authenticated, 202, 429,
503) mails an `ACCOUNT_DELETION` code to the session's address.
`DELETE /api/users/me` takes `{code}`; a wrong, expired, exhausted or
wrong-purpose code is 403 `urn:2doai:problem:re-auth-failed`, with attempts
counted. A `SIGN_IN` code cannot confirm a deletion and a deletion code cannot
open a session.

#### 5. Password remnants

**File**: `backend/.../user/User.java`, `auth/Utf8ByteLength.java` (removed),
affected javadoc

**Intent**: Leave no password concept in the code.

**Contract**: `User` no longer maps `password_hash`; its constructor is
`User(email, language)`. The column stays in the database, nullable and
unread. `PasswordEncoder` remains only for hashing codes.

#### 6. Test base

**File**: `backend/src/test/.../ApiTestBase.java` and the tests that construct
`User` or `UserPrincipal` directly

**Intent**: Keep every dependent suite signing in through one helper.

**Contract**: `givenLoggedInUser()` registers an address, reads the code from
`RecordingEmailSender` and posts it to `/api/sessions`. Its signature and return
value are unchanged; `confirmAddress` goes with `POST /api/verifications`.
Password-specific tests are deleted, not adapted. The URN guards at
`VerificationApiTest.java:440-470` take their values from live responses this
phase removes, so they are deleted with the two URNs. `User` constructors also
change in six test files outside the auth packages: `ProposalPersistenceTest`,
`ProposalSchedulerIntegrationTest`, `ProposalSchedulerTest`,
`ScheduledProposalTest`, `AiMemoryRepositoryTest`, `GoalPersistenceTest`; and
`AccountDeletionIntegrationTest` calls `registrationService.register` with a
password in eight places.

#### 7. Contract, runbook and living documentation

**File**: `context/foundation/openapi.yaml`,
`context/foundation/deployment-runbook.md`, `docs/index.html`

**Intent**: Publish the breaking contract and the rollback rule.

**Contract**: Spec version 2.0.0. `UserRegistration`, `Credentials` and
`AccountDeletion` lose `password`; `/verifications` and `EmailVerification` are
removed; `/users/me/deletion-codes` is added; `POST /users` is 202 without a
body and without 409; the two URNs are removed. The runbook records that a
rollback to release 1 is safe and what it restores. `docs/index.html` walked on
all nine surfaces, including decision register row 564 (the 409 rule is
resolved), the registration, login and deletion flows, both class diagrams, the
endpoint list and the stamp.

### Success Criteria:

#### Automated Verification:

- `POST /api/users` returns an identical 202 for a new and a known address, and the known address receives a sign-in code: `mvn test -Dtest=AuthApiTest`
- An account registered twice before verification can still be claimed by whoever reads the mailbox
- `POST /api/sessions` with a `password` field is rejected with 422
- Deletion with a deletion code succeeds; with a sign-in code, a wrong code or an expired code it returns 403 `re-auth-failed`
- A deletion code cannot open a session
- The re-auth URN guard across Java, `openapi.yaml` and `problems.ts` passes
- `ApiSurfaceTest` passes against `openapi.yaml` 2.0.0
- No occurrence of `password` remains in `backend/src/main` outside `PasswordEncoder` usage and migration files: `grep -rni password backend/src/main`
- `mvn test` passes; `node --test docs/index.test.mjs` passes

#### Manual Verification:

- Local, with the Phase 5 frontend or `curl`: register, sign in, request a deletion code, delete the account
- The release 1 image boots against a database holding an account with a null `password_hash` and signs it in by code
- `docs/index.html` opened in a browser; edited Mermaid diagrams render

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phases 4 and 5 deploy together: merge Phase 5 before the production deploy of Phase 4, or deploy backend and frontend in the same window.

---

## Phase 5: Password removal, frontend and records (release 2)

### Overview

The screens stop asking for a password, the delete dialog asks for a code, and
every document that states "e-mail + password" is superseded.

### Changes Required:

#### 1. Auth screens

**File**: `frontend/src/pages/AuthPage.tsx`, `pages/VerifyPage.tsx`,
`pages/LandingPage.tsx`

**Intent**: Both entry screens ask for an address and lead to the code screen.

**Contract**: `/login` and `/register` render an address-only form; both
navigate to `/verify`. The password field on `/register` and the 409 branch
are removed.

#### 2. Auth context and Problem types

**File**: `frontend/src/auth/auth-context.ts`, `auth/AuthProvider.tsx`,
`auth/problems.ts`

**Intent**: Remove the password from the client's API.

**Contract**: `register(email)`; new `requestDeletionCode()`; `deleteAccount(code)`.
`problems.ts` keeps only `RE_AUTH_FAILED`.

#### 3. Account deletion dialog

**File**: `frontend/src/auth/AccountMenu.tsx`

**Intent**: Confirm deletion with a mailed code.

**Contract**: The confirmation step first offers "send me a code", then shows a
eight-digit field and the irreversible submit. `RE_AUTH_FAILED` maps to
wrong-or-expired-code copy. The same resend cooldown as `/verify` applies.

#### 4. Copy

**File**: `frontend/src/i18n/pl.ts`, `frontend/src/i18n/en.ts`

**Intent**: Remove every mention of a password, including the FAQ.

**Contract**: Password keys deleted; FAQ answers at `pl.ts:37,40` and their
English twins rewritten; registration and deletion copy added.

#### 5. Decision records and project documents

**File**: `context/foundation/auth-session-model.md`, `prd.md`, `roadmap.md`,
`test-plan.md`, `deployment-runbook.md`, `CLAUDE.md`, `README.md`,
`context/changes/password-reset/change.md`

**Intent**: Supersede the recorded "e-mail + password" decision everywhere it is
stated, with the reason.

**Contract**: `auth-session-model.md` decision 3 marked superseded by a dated
section naming the code credential; the DEV-31 section amended from password to
code. `prd.md:79-81,177` and `roadmap.md:35,46,185,322` annotated with the new
decision and date. `test-plan.md:82-85` coverage rows updated. Runbook section
8.4 round trip and the quota paragraph at 347-351 rewritten. `CLAUDE.md:105` and
`README.md:28-30` updated. `change.md` title updated to describe the work.

#### 6. Living documentation

**File**: `docs/index.html`

**Intent**: The page describes what merged.

**Contract**: Full nine-surface walk: capability table rows 211 and 214,
frontend prose, the `Account`, `User` and `Verification code` terms, glossary
rows 2686-2693, the stamp.

### Success Criteria:

#### Automated Verification:

- No password field renders on `/login`, `/register` or the delete dialog: `npm test`
- Registration lands on `/verify` and a code opens a session for a new account
- The delete dialog requests a code, deletes with it, and shows the code copy on `RE_AUTH_FAILED`: `npx vitest run src/auth/AccountMenu.test.tsx`
- No occurrence of `password`, `hasło` or `haslo` remains in `frontend/src`: `grep -rniE "password|has[łl]o" frontend/src`
- `npm run lint`, `npm test` and `npm run build` pass
- `/check` is green
- `node --test docs/index.test.mjs` passes

#### Manual Verification:

- Production round trip on a fresh Gmail address: register, receive one mail, sign in by link, sign out, sign in by typed code
- Registering an address that already has an account produces the same screen and a sign-in mail
- Delete a throwaway account with a mailed code; a sign-in code is refused in the dialog
- The owner's account still signs in and its data is intact
- `docs/index.html` read top to bottom for any remaining password claim

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- `User.hasCodeAwaiting` across purpose, expiry, attempts and verified state
- `AuthMailBudget`: admit, exhaust, refund, day rollover
- Mail builders: link shape, URL-encoding of an address with `+`, both languages
- `VerifyPage` fragment parsing: missing, partial and malformed fragments
- Resend countdown with fake timers

### Integration Tests:

- REST Assured against a random port, as the existing suites do
- Sign-in by code: success, rotation, each failure cause indistinguishable
- First sign-in of an unverified account: verification, `user_registered` once
- Remember-me: cookie-only request, token rotation, logout, deletion, zero
  queries with a live session
- Deletion code purpose isolation in both directions
- Cross-boundary guards: re-auth URN, link path and fragment names, the
  15-minute figure, the eight-digit pattern
- Concurrency: parallel wrong guesses against one code; two requests sharing one
  remember-me cookie

### Manual Testing Steps:

1. Request a code on `/login`, sign in by typing it.
2. Request another, sign in by opening the link and pressing the button.
3. Restart the backend and confirm the session survives.
4. Request codes until the 60 s cooldown and the hourly limit both show.
5. Register an existing address and confirm the uniform answer.
6. Delete a throwaway account with a deletion code.
7. Repeat steps 1-2 in production on Gmail; record the `outlook.com` result.

## Performance Considerations

- A live session still validates in memory with no database query.
- The token table is read and touched once per device per browser launch or
  backend restart, and written at sign-in and logout. That stays far below the five-minute Neon idle window.
- Thirty-day in-memory sessions for 1-10 users are a negligible heap cost on the
  512 MB machine.
- The asynchronous mail executor has one worker and a bounded queue, so a burst
  cannot grow memory without limit.

## Migration Notes

- **V15** and **V16** are expand-only. The V14 image runs unharmed against both.
- **Existing accounts** need no data migration. A verified account signs in by
  code the moment release 1 is live; its stored hash is ignored after release 2.
- **Rollback from release 2 to release 1** is safe: release 1 tolerates a null
  hash and signs such accounts in by code. Rollback from release 1 to the V14
  image needs no data fix, because release 1 never writes a null hash.
- **Follow-up change** (not in this plan): drop `password_hash` once release 2
  has been stable.
- A backend restart during release 1's first deploy still signs everyone out
  once; from Phase 3 on it does not.

## References

- Frame brief: `context/changes/password-reset/frame.md`
- Session decision record: `context/foundation/auth-session-model.md`
- Lessons: `context/foundation/lessons.md:12-17,29-36,38-44`
- Prior plans: `context/changes/email-verification/plan.md`,
  `context/changes/account-and-auth/plan.md:101-106`
- Code machinery to reuse: `backend/.../auth/EmailVerificationService.java:116-281`,
  `auth/VerificationThrottle.java:50-142`
- Session establishment: `backend/.../session/SessionController.java:73-104`,
  `security/SecurityConfig.java:146-236`
- Test base: `backend/src/test/.../ApiTestBase.java:117-175`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Code sign-in, backend (release 1)

#### Automated

- [x] 1.1 Flyway applies V15 on a database at V14 and Hibernate validation passes — ee3d43b
- [x] 1.2 A verified account signs in with a mailed code and receives a rotated session and CSRF token — ee3d43b
- [x] 1.3 Wrong, expired, spent and sixth-attempt codes all return the same generic 401 on `POST /api/sessions` — ee3d43b
- [x] 1.4 A request with both or neither of `password` and `code` returns 422 — ee3d43b
- [x] 1.5 An unverified account's first code sign-in proves the address and logs `user_registered` exactly once — ee3d43b
- [x] 1.6 A code request for a verified address now sends mail; an unknown address still sends none and answers 202 — ee3d43b
- [x] 1.7 The budget answers 503 for every address once exhausted and refunds on a failed send — ee3d43b
- [x] 1.8 Parallel wrong guesses never exceed five per code — ee3d43b
- [x] 1.9 A mailed code has eight digits, and the length guards on `VerifyPage.tsx` and `openapi.yaml` pass — ee3d43b
- [x] 1.10 A guard test fails if the mail's link path or fragment names differ from `VerifyPage.tsx` — ee3d43b
- [x] 1.11 `ApiSurfaceTest` passes against `openapi.yaml` 1.9.0 — ee3d43b
- [x] 1.12 Password sign-in, registration and deletion tests still pass unchanged — ee3d43b
- [x] 1.13 `node --test docs/index.test.mjs` passes — ee3d43b

#### Manual

- [x] 1.14 Local run: request a code for a verified account and sign in with it — ee3d43b
- [x] 1.15 The mail reads correctly in Polish and English and the link has the expected shape — ee3d43b
- [x] 1.16 `docs/index.html` opened in a browser; edited Mermaid diagrams render — ee3d43b

### Phase 2: Code sign-in, frontend (release 1)

#### Automated

- [x] 2.1 Login submits the address, requests a code and lands on `/verify` with the address and `from` preserved — 3011448
- [x] 2.2 `/verify` prefills from the fragment, clears it, and does not sign in without a submit — 3011448
- [x] 2.3 A successful code returns the user to the originally requested route — 3011448
- [x] 2.4 The resend button counts down and honours `Retry-After` — 3011448
- [x] 2.5 `/login` renders no password field; `/register` still does — 3011448
- [x] 2.6 `npm run lint`, `npm test` and `npm run build` pass — 3011448
- [x] 2.7 Backend file guards on `VerifyPage.tsx`, `pl.ts` and `en.ts` pass — 3011448
- [x] 2.8 `/check` is green — 3011448

#### Manual

- [x] 2.9 Local: sign in by typed code and by link; the fragment is gone afterwards — 3011448
- [ ] 2.10 Link opened on a phone with the PWA installed: the code can be typed into the installed app
- [ ] 2.11 After the production deploy, the owner signs in with a mailed code and sees their existing goals
- [ ] 2.12 Control test: Gmail receives the mail; the `outlook.com` result is recorded in the runbook

### Phase 3: Long sessions that survive a restart (release 1)

#### Automated

- [x] 3.1 Flyway applies V16 and Hibernate validation passes — 77dcda4
- [x] 3.2 Signing in sets a remember-me cookie that is `HttpOnly` and `SameSite=Strict` — 77dcda4
- [x] 3.3 A request carrying only the remember-me cookie is authenticated, and the following request runs zero queries — 77dcda4
- [x] 3.4 That auto-login response carries a fresh `XSRF-TOKEN` cookie, and a mutation sent next succeeds — 77dcda4
- [x] 3.5 Two concurrent requests carrying the same remember-me cookie both succeed and the token survives — 77dcda4
- [x] 3.6 A request with a live session never reads the token table — 77dcda4
- [x] 3.7 The session cookie has no `Max-Age`; the remember-me cookie has 90 days and is renewed by an auto-login — 77dcda4
- [x] 3.8 Logout deletes the token and clears the cookie; the old cookie no longer authenticates — 77dcda4
- [x] 3.9 Account deletion removes the user's tokens — 77dcda4
- [x] 3.10 A forged or unknown token is refused with 401, never 500 — 77dcda4
- [x] 3.11 `mvn test` passes; `node --test docs/index.test.mjs` passes — 77dcda4

#### Manual

- [ ] 3.12 Local under `npm run dev` (StrictMode): sign in, restart the backend, reload the app, still signed in
- [ ] 3.13 Production: sign in, deploy the backend, reload, still signed in, and saving a goal works without a second reload
- [ ] 3.14 Neon's compute still suspends after five idle minutes with a signed-in tab left open
- [ ] 3.15 Closing and reopening the browser keeps the user signed in

### Phase 4: Password removal, backend (release 2)

#### Automated

- [ ] 4.1 `POST /api/users` returns an identical 202 for a new and a known address, and the known address receives a sign-in code
- [ ] 4.2 An account registered twice before verification can still be claimed by whoever reads the mailbox
- [ ] 4.3 `POST /api/sessions` with a `password` field is rejected with 422
- [ ] 4.4 Deletion with a deletion code succeeds; with a sign-in, wrong or expired code it returns 403 `re-auth-failed`
- [ ] 4.5 A deletion code cannot open a session
- [ ] 4.6 The re-auth URN guard across Java, `openapi.yaml` and `problems.ts` passes
- [ ] 4.7 `ApiSurfaceTest` passes against `openapi.yaml` 2.0.0
- [ ] 4.8 No occurrence of `password` remains in `backend/src/main` outside `PasswordEncoder` usage and migration files
- [ ] 4.9 `mvn test` passes; `node --test docs/index.test.mjs` passes

#### Manual

- [ ] 4.10 Local: register, sign in, request a deletion code, delete the account
- [ ] 4.11 The release 1 image boots against a database holding a null `password_hash` account and signs it in by code
- [ ] 4.12 `docs/index.html` opened in a browser; edited Mermaid diagrams render

### Phase 5: Password removal, frontend and records (release 2)

#### Automated

- [ ] 5.1 No password field renders on `/login`, `/register` or the delete dialog
- [ ] 5.2 Registration lands on `/verify` and a code opens a session for a new account
- [ ] 5.3 The delete dialog requests a code, deletes with it, and shows the code copy on `RE_AUTH_FAILED`
- [ ] 5.4 No occurrence of `password`, `hasło` or `haslo` remains in `frontend/src`
- [ ] 5.5 `npm run lint`, `npm test` and `npm run build` pass
- [ ] 5.6 `/check` is green
- [ ] 5.7 `node --test docs/index.test.mjs` passes

#### Manual

- [ ] 5.8 Production round trip on a fresh Gmail address: register, sign in by link, sign out, sign in by typed code
- [ ] 5.9 Registering an address that already has an account produces the same screen and a sign-in mail
- [ ] 5.10 Delete a throwaway account with a mailed code; a sign-in code is refused in the dialog
- [ ] 5.11 The owner's account still signs in and its data is intact
- [ ] 5.12 `docs/index.html` read top to bottom for any remaining password claim

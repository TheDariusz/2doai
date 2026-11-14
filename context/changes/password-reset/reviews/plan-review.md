<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Passwordless Sign-in

- **Plan**: context/changes/password-reset/plan.md
- **Mode**: Deep
- **Date**: 2026-11-14
- **Verdict**: REVISE (SOUND after fixes; all ten findings applied to the plan)
- **Findings**: 3 critical, 5 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | WARNING |
| Architectural Fitness | WARNING |
| Blind Spots | FAIL |
| Plan Completeness | WARNING |

## Grounding

6/7 paths ✓ (`session/LoginRequest.java` is really `auth/LoginRequest.java`, F10), symbols ✓, brief↔plan ✓, Progress↔Phase ✓.

Evidence was read from the code and from the Spring Security 7.1.2 and Tomcat 11.0.27 sources. Nothing was run against a live server.

## Findings

### F1 — A six-digit code is too weak as the only credential

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1 — Code redemption
- **Detail**: 5 codes an hour, each worth 5 guesses, is 600 guesses a day against 1,000,000 values: about a 20% yearly chance of entering a targeted account. Nothing caps wrong guesses per day. The five-guess cap is not atomic (`UserRepository.java:129-132` has no `attempts < MAX` predicate), so parallel guesses get roughly 9 per code.
- **Fix A ⭐ Recommended**: 8-digit code, atomic attempt increment
  - Strength: about 0.2% per year at the same limits; no new state; no lockout lever.
  - Tradeoff: longer to type; `CODE_LENGTH`, the `\d{6}` guards, openapi and copy move.
  - Confidence: HIGH — arithmetic; the link prefill makes typing the minority path.
  - Blind spot: None significant.
- **Fix B**: keep 6 digits, in-memory daily cap on wrong guesses per address, atomic increment
  - Strength: keeps the current contract and copy.
  - Tradeoff: resets on deploy and eviction; lets anyone block a victim's sign-in for the day.
  - Confidence: MEDIUM.
  - Blind spot: cap value not modelled.
- **Decision**: FIXED via Fix A

### F3 — Rotating remember-me tokens fail badly on concurrent use

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Phase 3 — Security wiring; criterion 3.7
- **Detail**: Spring rotates the token on every auto-login. A second request with the old cookie raises `CookieTheftException`, which deletes all of the user's tokens and escapes above `ExceptionTranslationFilter` (a 500, not the planned 401). Two restored tabs trigger it, as does StrictMode's double bootstrap in the plan's own local manual check.
- **Fix A ⭐ Recommended**: non-rotating token (custom services validating series + value)
  - Strength: no race; a mismatch is an invalid cookie and a 401.
  - Tradeoff: loses theft detection.
  - Confidence: HIGH — failure path read from library source.
  - Blind spot: not executed against a running server.
- **Fix B**: keep rotation, map `CookieTheftException` to 401
  - Strength: keeps theft detection.
  - Tradeoff: spurious sign-outs on all devices remain.
  - Confidence: MEDIUM.
  - Blind spot: frequency of multi-tab restores unknown.
- **Decision**: FIXED via Fix A

### F4 — Remember-me auto-login deletes the CSRF cookie

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 3 — Security wiring
- **Detail**: `CsrfAuthenticationStrategy` clears `XSRF-TOKEN` on auto-login after `CsrfCookieFilter` has already run. `client.ts` refuses non-GET requests without the cookie, so the first mutation after a deploy fails.
- **Fix**: require a fresh `XSRF-TOKEN` on the auto-login response, with a test.
- **Decision**: FIXED

### F2 — A third party can block someone's sign-in

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Phase 1 — Mail and throttle; Open Risks
- **Detail**: The throttle is keyed by address on a public endpoint. Anyone who knows an address can spend its 5 codes an hour, and each admitted request overwrites the owner's pending code.
- **Fix A ⭐ Recommended**: accept, record in Open Risks, name the per-IP edge limit as the follow-up
  - Strength: honest scope; long sessions make sign-in rare.
  - Tradeoff: a targeted user can be kept out while the attack runs.
  - Confidence: MEDIUM — acceptable at 1–10 users.
  - Blind spot: no abuse observed either way.
- **Fix B**: make the per-IP edge limit a prerequisite of release 2
  - Strength: closes the lever before mail is the only way in.
  - Tradeoff: pulls deferred infrastructure work into scope.
  - Confidence: MEDIUM.
  - Blind spot: edge limit design not researched.
- **Decision**: FIXED via Fix A (risk accepted and recorded)

### F6 — "30 idle days" is not what the plan builds

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Phase 3 — session lifetime; Desired End State
- **Detail**: Tomcat sets the session cookie's `max-age` only at creation, and the remember-me cookie is rewritten only on auto-login. Both would expire 30 days after sign-in even with daily use.
- **Fix**: no `max-age` on the session cookie; remember-me validity 90 days, renewed on each auto-login; end state restated.
  - Strength: each browser launch or deploy renews the token.
  - Tradeoff: one DB read per browser launch instead of per restart.
  - Confidence: HIGH — read from Tomcat source.
  - Blind spot: a non-rotating token needs its expiry extended explicitly (now in the Phase 3 contract).
- **Decision**: FIXED

### F8 — Async mail is underspecified and will break ~40 tests

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 change 5; Phase 4 change 1
- **Detail**: Only the SMTP send left the request thread; the code write and the account insert still ran before the 202 for known addresses only. No executor pattern exists in the codebase, and tests read the mailed code right after the response.
- **Fix**: lookup, issue and send in one named executor task; synchronous executor in tests.
- **Decision**: FIXED

### F7 — The password fallback on /login serves no one

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Phase 2 — Login screen
- **Detail**: The only real account cannot use its password. The dual form would be built in Phase 2 and deleted in Phase 5.
- **Fix**: `/login` is code-only in Phase 2; the backend accepts passwords until Phase 4.
- **Decision**: FIXED

### F5 — Remember-me wiring details left to guesswork

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 3 — Security wiring
- **Detail**: Services must be a declared bean with `alwaysRemember`; `SameSite` needs `setCookieCustomizer`, which the cancel cookie ignores; the app's `DELETE` endpoints use `apiLogoutHandler`; the repository contract is keyed by username. The plan's warning about saving the context was unnecessary.
- **Fix**: replace the "verify this" note with these facts.
- **Decision**: FIXED

### F9 — Behaviour that hangs off the removed provider

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 change 4; Phase 4 change 2
- **Detail**: The code path must throw an `AuthenticationException` outside the redeem transaction; the 503 for a database failure at sign-in comes from the provider; `UserVerified` must be published inside a transaction.
- **Fix**: add these three invariants to the contracts.
- **Decision**: FIXED

### F10 — One wrong path, and guards that derive from removed behaviour

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phases 1 and 4
- **Detail**: `LoginRequest` lives in `auth/`. The URN guards at `VerificationApiTest.java:440-470` take values from responses Phase 4 removes. Password-era `User` constructors sit in six test files outside the auth packages.
- **Fix**: correct the path and name these tests in Phase 4.
- **Decision**: FIXED

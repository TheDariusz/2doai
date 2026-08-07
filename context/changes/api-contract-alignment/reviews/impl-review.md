<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: API Contract Alignment (DEV-31)

- **Plan**: `context/changes/api-contract-alignment/plan.md`
- **Scope**: Full plan (Phases 1–2 of 2)
- **Date**: 2026-08-05
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 4 observations
- **Commits reviewed**: `19e3034` (p1), `3eb176e` (p2), `8923006` (epilogue); base `565f157`

All 10 planned changes verdict MATCH. No "What We're NOT Doing" violations. All six
automated success criteria re-run green at HEAD; after triage the backend suite is 115
tests (one added by F2's fix).

Two deviations were disclosed to the reviewer up front and confirmed as intentional:
the DELETE 403 became an inline response rather than a `$ref` to
`components/responses/Forbidden` (the shared description cannot carry two-cause
discrimination without mis-describing logout and login), and criterion 1.4's grep window
had to be `-A11` rather than `-A12`.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — The no-CSRF-cookie guard's comment now states the opposite of the truth

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: frontend/src/api/client.ts:45-48
- **Detail**: The guard justified itself with "its 403 is indistinguishable from the 403 a wrong re-auth password produces" — the exact condition this change removed. The guard is still correct; only its stated reason was false. In a repo where comments carry the design record, this is the note a future reader would use to decide the guard can be deleted.
- **Fix**: Reword to the surviving reason — without a token there is nothing for the double-submit check to match, so the round-trip is not worth spending.
  - Strength: Keeps the guard while removing a claim that would mislead the next reader.
  - Tradeoff: None — comment-only.
  - Confidence: HIGH — the behavioural justification is unchanged and independently true.
  - Blind spot: None significant.
- **Decision**: FIXED

### F2 — The negative CSRF assertion sits on the endpoint where the two 403s don't collide

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: backend/src/test/java/com/thedariusz/todoai/AuthApiTest.java:159-176
- **Detail**: `rejectsAnAuthenticatedMutationCarryingNoCsrfToken` asserts the CSRF 403 lacks the URN, but hits `DELETE /api/sessions/current`, while the ambiguity this ticket resolves lives on `DELETE /api/users/me` — the only endpoint that can answer 403 for either cause, and the one `openapi.yaml:124-135` documents. Both route through the same `ProblemDetailsSecurityHandler.handle`, so the assertion is true; it just was not exercising the contract it defends.
- **Fix A ⭐ Recommended**: Add a CSRF-less `DELETE /api/users/me` case asserting 403 + `type` != the URN, leaving the existing test as-is.
  - Strength: Pins the contract on the endpoint the spec describes; the existing test keeps covering the logout path it was written for.
  - Tradeoff: One more test on an already 25-test class.
  - Confidence: HIGH — same `csrfAware()`/`client()` helpers, same shape as the neighbouring test.
  - Blind spot: None significant.
- **Fix B**: Move the assertion onto `/users/me` instead of duplicating.
  - Strength: No net test growth.
  - Tradeoff: The logout 403 loses the assertion.
  - Confidence: MEDIUM — narrows coverage rather than widening it.
  - Blind spot: Whether a future endpoint reuses the logout path's handler.
- **Decision**: FIXED via Fix A — `deniesADeletionCarryingNoCsrfTokenWithoutTheReAuthProblemType`, suite now 115 tests

### F3 — problem?.type reaches a `string | undefined` field with no runtime check

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/api/client.ts:70
- **Detail**: A body of `{"type": 42}` stores a number in a field typed `string | undefined`. Harmless today — compared with `===`, never rendered — and `detail` already carries the same latent lie. Every other body shape (null, non-JSON, array, missing `type`) was verified to yield `undefined` correctly.
- **Fix**: `typeof problem?.type === 'string' ? problem.type : undefined`
- **Decision**: SKIPPED — harmless today; the same looseness already applies to `detail`

### F4 — The URN lives as a literal in six files with no test crossing the boundary

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: ApiExceptionHandler.java:31, AuthApiTest.java:175/221, openapi.yaml:127, AccountMenu.tsx:7, AccountMenu.test.tsx:86, client.test.ts:47/52
- **Detail**: All nine occurrences are byte-identical today, but each side asserts against its own copy. A backend rename that updates the Java and its tests leaves every frontend test green while the SPA silently falls back to generic copy. Same drift class as the spec↔enum gap this ticket was filed to fix.
- **Fix**: A guard that reads the URN from `openapi.yaml` (the anchor) and checks both implementations against it.
- **Decision**: ACCEPTED-AS-RULE: "A contract value duplicated across the stack needs one guard that spans the boundary" (appended to `context/foundation/lessons.md`). Code left unchanged by explicit choice.

### F5 — The CSRF-403 copy no longer offers the reload the contract names

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/src/auth/AccountMenu.tsx:46
- **Detail**: `openapi.yaml:128` describes the CSRF cause as one "which a page reload fixes"; the else-branch copy dropped the old "odśwież stronę" hint. This is exactly what the plan specified, so it is not drift. Mitigating: Spring's deferred CSRF token re-primes the `XSRF-TOKEN` cookie on the denial response, so a plain retry usually succeeds.
- **Fix**: Restore a reload hint in the generic branch, or give the non-URN 403 its own copy.
- **Decision**: SKIPPED — plan-specified copy; retry works because the token is re-primed

### F6 — Two success-criteria commands as written don't run here

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: plan.md:191-192
- **Detail**: 1.3's `python3 -c "import yaml"` fails — no PyYAML on this machine; verified with Ruby's Psych instead. 1.4's `grep -A12` swallows `example: HEALTH` as a twelfth code and always reports a spurious diff; `-A11` is correct. Both pass in corrected form.
- **Fix**: Correct both commands in the plan's Automated Verification block, with a note on why.
- **Decision**: FIXED

## Verification at HEAD (post-triage)

| Check | Result |
|---|---|
| `mvn test` | PASS — 115 tests, 0 failures (2 skipped: `OpenRouterLiveTest`) |
| `AuthApiTest` two named contract tests | PASS |
| `openapi.yaml` parses (Ruby/Psych) | PASS |
| Eleven codes vs `V2__seed_categories.sql`, in order | PASS — empty diff |
| `npm test` | PASS — 36 tests, 7 files |
| `npm run lint` | PASS |
| `npm run build` | PASS |
| `node --test docs/index.test.mjs` | PASS — 12 tests |

Manual criteria 1.5, 1.6, 2.5, 2.6, 2.7 were confirmed by the developer against a running
stack and by reading the two Phase 1 doc diffs.

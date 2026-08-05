# API Contract Alignment (DEV-31) Implementation Plan

## Overview

`openapi.yaml` for the account-and-auth slice disagrees with the code it describes in three places.
Two were reported in DEV-31 (401 vs 403 on a failed re-authentication; the life-domain code list);
one was found while researching this plan (an undocumented 503). The status disagreement is not a
typo — it exposes that two different 403s are indistinguishable to a client — so the fix is a
machine-readable discriminator, not just a YAML edit.

## Current State Analysis

**The re-authentication status.** `UserController:100-103` throws `ReAuthenticationFailedException`
when the confirmation password does not match; `ApiExceptionHandler:50-53` maps it to **403**; and
`AuthApiTest:205-223` asserts 403. `openapi.yaml:122-125` documents the inverse (401 for a wrong
password, 403 for CSRF). The code is right; the spec is stale.

Both 403s a client can receive on that endpoint are currently identical on the wire:

| Cause | Written by | status | title | type |
| --- | --- | --- | --- | --- |
| Wrong re-auth password | `ApiExceptionHandler:52` | 403 | `Forbidden` (Spring default) | `about:blank` |
| CSRF token missing/invalid | `ProblemDetailsSecurityHandler:72` | 403 | `Forbidden` (Spring default) | `about:blank` |

Only the `detail` prose differs, which is exactly what RFC 9457 says clients must not parse. That is
why `AccountMenu.tsx:40-44` softened its copy to *"Nie udało się potwierdzić. Sprawdź hasło lub
odśwież stronę."*

**The scenario in the ticket does not actually occur.** DEV-31 motivates the fix with: session
expires → CSRF token goes stale → the delete 403s from the CSRF filter → nothing bounces the user to
`/login`. That requires a session-bound CSRF token repository. This project uses
`CookieCsrfTokenRepository.withHttpOnlyFalse()` (`SecurityConfig:142`) — stateless double-submit, the
token lives in its own cookie and is validated against that same request's cookie. An expired session
leaves the `XSRF-TOKEN` cookie valid, so the DELETE passes `CsrfFilter`, reaches the authorization
filter unauthenticated, and gets a **401** from the entry point — which `client.ts:61-66` turns into
the `session-expired` event that drops the SPA to `/login`. The user-facing trap is not real. The
contract ambiguity is, and it is what this plan fixes.

**The domain codes.** `openapi.yaml:308-319` lists `HEALTH, RELATIONSHIPS, CAREER, FINANCE,
PERSONAL_GROWTH, RECREATION, SPIRITUALITY, HOME, COMMUNITY, CREATIVITY, EMOTIONS`. `LifeDomain.java`
and `V2__seed_categories.sql` agree with each other on a different list: `HEALTH, FINANCE, CAREER,
EDUCATION, RELATIONSHIPS, HOME, LEISURE, ADMIN, SAFETY, TRANSPORT, INNER_GROWTH`. Six of eleven codes
differ, and the spec's ordering is not `display_order` either. `CategorySyncCheck` compares the enum
to the table at boot; the spec is outside that loop, which is why this drifted silently.

**The undocumented 503.** `ProblemDetailsSecurityHandler:50-55` separates
`AuthenticationServiceException` (a pool timeout, a Neon cold start that overruns) from bad
credentials and answers **503**, deliberately and with a Javadoc explaining why. `DaoAuthenticationProvider`
only runs on `POST /api/sessions` — session-carried requests never touch it — so 503 is reachable on
login and nowhere else. No operation in the spec lists it.

## Desired End State

`openapi.yaml` describes what the server actually does on all three points, a client can tell a
mistyped password from any other 403 by reading `type`, and `AccountMenu` says *"Nieprawidłowe
hasło."* again. Verified by: `AuthApiTest` asserting the `type` URN on the re-auth 403 and its
absence on the CSRF 403; `AccountMenu.test.tsx` asserting the precise copy; and a read of the spec's
three corrected sections against `LifeDomain.java`, `V2__seed_categories.sql`, and
`ProblemDetailsSecurityHandler`.

### Key Discoveries:

- `Problem.type` is **already in the contract** (`openapi.yaml:336-339`, `default: about:blank`), so
  adding a `type` URN needs no schema change — only a handler line and a response description.
- `ApiError` (`client.ts:11-19`) carries `status` and `detail` only; `client.ts:69-70` reads the
  Problem JSON body and drops every field except `detail`. The frontend cannot key off `type` today.
- The CSRF-403 the frontend worries about is largely pre-empted client-side already: `client.ts:43-49`
  refuses to send a mutation with no `XSRF-TOKEN` cookie and throws `ApiError(0, …)` instead, which is
  the realistic "cookie missing" path.
- `ReAuthenticationFailedException`'s Javadoc already argues the 403 choice well. What is missing from
  the written record is the *rejected* alternatives (401, 422) and the `type` decision.
- Contract tests should pin the wire value as a **literal**, not import a shared constant — a literal
  in `AuthApiTest` is what catches an accidental rename of the URN in `ApiExceptionHandler`.

## What We're NOT Doing

- **Not** moving `openapi.yaml` out of `context/changes/account-and-auth/`, even though `/10x-archive`
  will eventually move that folder into `context/archive/`. Real structural problem, separate ticket.
- **Not** adding an automated spec↔enum drift guard (a test parsing the YAML's `x-extensible-enum`
  against `LifeDomain`). Explicitly deferred — this plan fixes the drift, not the class of drift.
- **Not** extending `CategorySyncCheck` to guard `name_pl` / `display_order`. Noted as a margin
  observation in DEV-31's comment; out of scope here.
- **Not** giving the CSRF 403 its own `type`. One discriminator is enough: "is it the re-auth URN or
  not" answers the only question a client has. Adding a second URN doubles the contract surface for no
  new client capability.
- **Not** auditing every operation against its controller. Register, login, logout and categories were
  spot-checked during research and match; the 503 was the only additional gap found.
- **Not** changing any HTTP status. 403 stays 403.

## Implementation Approach

Backend and contract first, so Phase 2 keys off a URN that actually exists on the wire rather than a
hypothetical one. Both phases are test-first per the project convention: the assertion that names the
new wire value is written and seen to fail before the handler is touched.

## Critical Implementation Details

**Spring serializes `ProblemDetail.type` even when unset** — it defaults to `about:blank` rather than
null, so an assertion that the CSRF 403 lacks the re-auth URN should be written as *not equal to the
URN* rather than *absent*. That phrasing is correct whether Spring emits `"about:blank"` or omits the
field, and does not need verifying first.

## Phase 1: Backend + Contract

### Overview

Give the re-auth failure a `type` URN and a title of its own, pin both in `AuthApiTest`, correct all
three drifts in `openapi.yaml`, and record the decision.

### Changes Required:

#### 1. The re-auth Problem JSON

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/ApiExceptionHandler.java`

**Intent**: `handleReAuthenticationFailed` (line 50-53) currently returns a bare
`ProblemDetail.forStatusAndDetail(FORBIDDEN, …)`, which inherits Spring's default `about:blank` type
and `Forbidden` title — the same pair the CSRF denial emits. Set a distinct `type` and `title` so the
two are machine-distinguishable, and note in a short comment *why* (the CSRF 403 from
`ProblemDetailsSecurityHandler` is the other 403 on this endpoint).

**Contract**: response body gains `"type": "urn:2doai:problem:re-auth-failed"` and
`"title": "Re-authentication failed"`. Status stays **403**; `detail` stays the exception's message
(*"The password you entered is incorrect"*). The URN lives as a private constant here — this class is
the only producer.

#### 2. Contract tests for both 403s

**File**: `backend/src/test/java/com/thedariusz/todoai/AuthApiTest.java`

**Intent**: Written first, before change #1. `rejectsDeletionWhenTheReAuthPasswordIsWrong` (line
205-223) asserts status and `detail` prose today; add the `type` and `title` assertions that make the
discriminator a tested contract. Then extend `rejectsAnAuthenticatedMutationCarryingNoCsrfToken`
(line 159-170) with the negative half — a CSRF 403 must *not* carry the re-auth URN. Without that
second assertion nothing stops a future refactor from typing both 403s identically again, which is
the exact bug this ticket is about.

**Contract**: `rejectsDeletionWhenTheReAuthPasswordIsWrong` asserts
`type == "urn:2doai:problem:re-auth-failed"` and `title == "Re-authentication failed"` (literals, not
an imported constant). `rejectsAnAuthenticatedMutationCarryingNoCsrfToken` asserts
`type != "urn:2doai:problem:re-auth-failed"`.

#### 3. The contract — the two reported drifts and the 503

**File**: `context/changes/account-and-auth/openapi.yaml`

**Intent**: Three independent corrections.

(a) `DELETE /users/me` responses (lines 119-127): the 401 and 403 comments are inverted relative to
the implementation. 401 means not logged in / session expired — never a wrong password. 403 covers
both a wrong re-auth password (carrying the URN) and a missing/invalid CSRF token (`about:blank`);
document both sub-cases and name the URN so the SPA has a contract to code against, not an
observation.

(b) `Category.code` `x-extensible-enum` (lines 308-319): replace the six wrong codes with the real
ones and put all eleven in `display_order`, matching `V2__seed_categories.sql` and the declaration
order of `LifeDomain.java`. Keep `x-extensible-enum` (Zalando #112) — this stays open-ended.

(c) A `ServiceUnavailable` entry under `components/responses`, `$ref`'d from **`POST /sessions` only**.
`DaoAuthenticationProvider` is the only path that reaches `loadUserByUsername`, so login is the only
operation that can produce the 503 from `ProblemDetailsSecurityHandler:53`; adding it elsewhere would
document a response the server cannot send.

**Contract**: the eleven codes become `HEALTH, FINANCE, CAREER, EDUCATION, RELATIONSHIPS, HOME,
LEISURE, ADMIN, SAFETY, TRANSPORT, INNER_GROWTH`; a `ServiceUnavailable` response returning
`application/problem+json` with the `Problem` schema; the DELETE 403 description names
`urn:2doai:problem:re-auth-failed`. No schema change to `Problem` — `type` is already declared.

#### 4. The decision record

**File**: `context/foundation/auth-session-model.md`

**Intent**: Append a section recording the DEV-31 decision. The Javadoc on
`ReAuthenticationFailedException` already argues *for* 403; what is unwritten is what was rejected and
why. Record: 403 kept; a `type` URN added as the discriminator; **401 rejected** (a 401 on an
authenticated call is what every SPA reads as "session expired" and would bounce the user to login on
a typo); **422 rejected** (it would collide with the Bean Validation 422 for an empty or oversized
password, so a discriminator would still be needed — 422 buys nothing and costs a status change).
Also record the finding that the stale-CSRF-after-session-expiry scenario is unreachable under
`CookieCsrfTokenRepository`, so nobody re-derives it. Written in Polish, matching the document.

**Contract**: a new `## ` section after `## Konsekwencje`, cross-referenced from `prd_refs: FR-019`
context. The URN appears here as the canonical decided value.

### Success Criteria:

#### Automated Verification:

- Backend suite passes: `cd backend && mvn test`
- The two contract tests pass by name: `mvn test -Dtest=AuthApiTest#rejectsDeletionWhenTheReAuthPasswordIsWrong+rejectsAnAuthenticatedMutationCarryingNoCsrfToken`
- `openapi.yaml` still parses: `python3 -c "import yaml,sys; yaml.safe_load(open('context/changes/account-and-auth/openapi.yaml'))"`
- The spec's eleven codes match the seed, in order: `diff <(grep -A12 'x-extensible-enum' context/changes/account-and-auth/openapi.yaml | grep -oE '[A-Z_]{3,}') <(grep -oE "'[A-Z_]{3,}'" backend/src/main/resources/db/migration/V2__seed_categories.sql | tr -d "'")`

#### Manual Verification:

- The new `auth-session-model.md` section reads as a decision record, not a changelog — a reader six months out learns why 401 and 422 were rejected
- The DELETE 403 description in `openapi.yaml` is enough for a client author to implement the discrimination without reading the Java

**Implementation Note**: After completing this phase and all automated verification passes, pause for
manual confirmation before proceeding to Phase 2.

---

## Phase 2: Frontend

### Overview

Carry `type` through the API client and let `AccountMenu` say what is actually wrong.

### Changes Required:

#### 1. `ApiError` carries the problem type

**File**: `frontend/src/api/client.ts`

**Intent**: `ApiError` (lines 11-19) exposes `status` and the message only, and line 70 discards every
Problem JSON field but `detail`. Add an optional readonly `type` populated from the parsed body, so
callers can discriminate without parsing prose. Optional keeps the existing two-argument construction
at line 48 (the status-0 no-CSRF-cookie guard) untouched.

**Contract**: `new ApiError(status: number, detail: string, type?: string)`, with a
`readonly type?: string` field fed from `problem?.type` at line 70.

#### 2. The precise deletion error

**File**: `frontend/src/auth/AccountMenu.tsx`

**Intent**: `onDelete`'s catch (lines 36-44) branches on `status === 403` and shows copy hedged across
two causes. Branch on the URN instead and restore *"Nieprawidłowe hasło."*; everything else — including
a CSRF 403 — falls through to the existing generic retry copy. Two branches, not three: the realistic
missing-cookie case is already caught client-side at `client.ts:43-49` as `ApiError(0, …)` and lands in
the generic branch either way. Replace the now-stale comment about the two indistinguishable 403s.

**Contract**: `failure instanceof ApiError && failure.type === 'urn:2doai:problem:re-auth-failed'` →
`'Nieprawidłowe hasło.'`; otherwise the existing `'Nie udało się usunąć konta. Spróbuj ponownie.'`.

#### 3. Frontend tests

**Files**: `frontend/src/auth/AccountMenu.test.tsx`, `frontend/src/api/client.test.ts`

**Intent**: Written first, before changes #1 and #2. `AccountMenu.test.tsx:75-89` builds
`new ApiError(403, 'Re-authentication failed')` and asserts only `/hasło/i`, which the hedged copy also
satisfies — it would pass without the fix. Give it the URN and assert the exact new copy, then add a
sibling case: a 403 *without* the URN gets the generic copy and still does not end the session. In
`client.test.ts`, assert that a Problem JSON `type` survives onto the thrown `ApiError` — that is the
carrying contract, and nothing else tests it.

**Contract**: `AccountMenu.test.tsx` covers both 403 shapes (with and without the URN);
`client.test.ts` covers `type` present and absent.

### Success Criteria:

#### Automated Verification:

- Frontend tests pass: `cd frontend && npm test`
- Lint passes: `cd frontend && npm run lint`
- Typecheck + build pass: `cd frontend && npm run build`
- Full gate green: `/check`

#### Manual Verification:

- Against a running backend: log in, open "Usuń konto", submit a wrong password → the alert reads
  *"Nieprawidłowe hasło."* and the session survives (the header still shows the email)
- Submitting the correct password still deletes and routes to `/login`
- DevTools Network shows `type: "urn:2doai:problem:re-auth-failed"` on the 403 body

---

## Testing Strategy

### Unit Tests:

- `client.test.ts` — `type` carried onto `ApiError` when present, `undefined` when the body has none
- `AccountMenu.test.tsx` — 403 + URN → precise copy; 403 without URN → generic copy; neither ends the session

### Integration Tests:

- `AuthApiTest` (REST Assured, real HTTP, full security chain) — the re-auth 403 carries the URN and
  the new title; the CSRF 403 does not carry the URN

### Manual Testing Steps:

1. `cd backend && mvn spring-boot:run`, then `cd frontend && npm run dev`
2. Register and log in
3. "Usuń konto" → wrong password → expect *"Nieprawidłowe hasło."*, session intact
4. Inspect the 403 body in DevTools for the URN
5. Retry with the correct password → 204, redirect to `/login`

## Migration Notes

None. No schema change, no status change, and `type` is an addition to a response body — a client that
ignores it behaves exactly as today. The frontend and backend can ship independently in either order.

## References

- Linear: DEV-31 — https://linear.app/thedariusz-dev/issue/DEV-31
- The contract being corrected: `context/changes/account-and-auth/openapi.yaml`
- Decision record extended: `context/foundation/auth-session-model.md`
- Why the ticket's CSRF scenario does not occur: `SecurityConfig.java:142` (`CookieCsrfTokenRepository`)
- The 503 the spec is missing: `ProblemDetailsSecurityHandler.java:50-55`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Backend + Contract

#### Automated

- [x] 1.1 Backend suite passes: `mvn test` — 19e3034
- [x] 1.2 Both contract tests pass by name — 19e3034
- [x] 1.3 `openapi.yaml` still parses — 19e3034
- [x] 1.4 The spec's eleven codes match the seed, in order — 19e3034

#### Manual

- [x] 1.5 The new `auth-session-model.md` section reads as a decision record — 19e3034
- [x] 1.6 The DELETE 403 description is implementable without reading the Java — 19e3034

### Phase 2: Frontend

#### Automated

- [x] 2.1 Frontend tests pass: `npm test` — 3eb176e
- [x] 2.2 Lint passes: `npm run lint` — 3eb176e
- [x] 2.3 Typecheck + build pass: `npm run build` — 3eb176e
- [x] 2.4 Full gate green: `/check` — 3eb176e

#### Manual

- [x] 2.5 Wrong password shows "Nieprawidłowe hasło." and the session survives — 3eb176e
- [x] 2.6 Correct password still deletes and routes to `/login` — 3eb176e
- [x] 2.7 DevTools shows the URN on the 403 body — 3eb176e

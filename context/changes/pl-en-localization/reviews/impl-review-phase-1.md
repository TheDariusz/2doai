<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Polish + English Localization

- **Plan**: `context/changes/pl-en-localization/plan.md`
- **Scope**: Phase 1 of 6 — Account language and its transport
- **Commit reviewed**: `130f84d`
- **Date**: 2026-09-04
- **Verdict**: NEEDS ATTENTION → all findings triaged; 6 fixed, 3 amended in the plan, 1 deferred
- **Findings**: 0 critical, 2 warnings, 8 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING — three plan-text defects, no code defects |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — AppLanguage calls itself the wire anchor without the guard

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `context/foundation/openapi.yaml:733`
- **Detail**: The spec comment claims anchor status "the way GoalLayer is for its three", but
  `GoalLayer` earns it through `GoalApiTest.publishesTheWireEnumsTheContractAnchors` (`:601`), which
  reads the spec and compares the set against the Java enum and the SPA type. `AppLanguage` had no
  such check — `AppLanguageTest` only asserted `of()`, the English names and `name()` length. This is
  the exact failure `lessons.md` records ("A contract value duplicated across the stack needs one
  guard that spans the boundary"); Phase 4 adds a third copy in the SPA.
- **Fix**: `AuthApiTest.publishesTheLanguageLiteralsTheContractAnchors` — loads `openapi.yaml`,
  extracts `AppLanguage`'s `x-extensible-enum`, compares it as a set to the enum's constant names.
  Verified to fail: dropping `EN` from the spec turns it red.
- **Decision**: FIXED

### F2 — PATCH /users/me writes unconditionally (Neon idleness)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `user/UserSettingsService.java` (was `user/UserController.java:122`)
- **Detail**: One statement per call, but no comparison against the current value, so a redundant
  PATCH still resets Neon's ~5-min idle timer. Harmless while a person clicks it by hand; the risk is
  a Phase 4 "reconcile browser locale on boot" line turning it into one write per session, with
  nothing failing loudly on the paid Launch plan.
- **Fix**: Documented in `UserSettingsService.changeLanguage`'s javadoc, including why the obvious
  server-side guard is wrong — `and u.preferredLanguage <> :language` would match 0 rows for a no-op,
  which the method reads as "account gone" and answers 401, logging out a user who re-selected their
  current language. The client-side guard is queued in `follow-ups/review-fixes.md` for Phase 4.
- **Decision**: FIXED (documented) + queued for Phase 4

### F3 — Plan's "422 for anything outside the enum" is a plan defect

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Plan Adherence
- **Location**: `plan.md`, Phase 1 item 5
- **Detail**: An out-of-enum literal fails Jackson deserialization and never reaches the 422 handler;
  it is a 400, matching `GoalApiTest.rejectsUnknownWireLiteralsWith400` and the spec's own prose.
  Forcing 422 would need a custom deserializer and would make `/users/me` the one endpoint answering
  a bad enum unlike every goal endpoint. Both review agents reached this independently.
- **Fix**: Plan item 5 amended to state the 400/422 split, with a blockquote recording the correction.
- **Decision**: FIXED (plan amended)

### F4 — Criterion 1.2 credits ApiSurfaceTest with enforcement it lacked

- **Severity**: OBSERVATION
- **Impact**: 🔎 MEDIUM
- **Dimension**: Success Criteria
- **Location**: `plan.md` Phase 1 item 7, `ApiSurfaceTest.java`
- **Detail**: The test compared **path sets**. `/users/me` already existed for `GET` and `DELETE`, so
  a missing `patch:` block would not have gone red — the plan's "`ApiSurfaceTest` fails until it
  does" only ever held for a brand-new path. Phases 2–3 also add to existing paths.
- **Fix**: `ApiSurfaceTest` widened to compare **method + path** operations in both directions
  (`specifiesEveryOperationTheServerPublishes`), filtering non-operation Path Item keys and reporting
  a method-less mapping as the unmatchable `ANY`. Passed unchanged against the current spec, so no
  pre-existing method-level gaps existed; verified to fail when the `patch:` block is removed. Plan
  item 7 amended to record what was actually true before.
- **Decision**: FIXED (test widened + plan amended)

### F5 — Rebuilt token dropped Authentication.getDetails()

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Safety & Quality
- **Location**: `security/AuthenticatedSession.java` (was `user/UserController.java:132`)
- **Detail**: `UsernamePasswordAuthenticationToken.authenticated(...)` copies credentials and
  authorities but not details. Inert today — the JSON login path never sets them — but the day
  anything attaches them, this endpoint would erase them mid-session for exactly the users who
  switched language.
- **Fix**: `refreshed.setDetails(current.getDetails())`, with a comment on why.
- **Decision**: FIXED

### F6 — Item 3 built as a Locale handler argument, not LocaleContextHolder

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Plan Adherence
- **Location**: `i18n/LocaleConfig.java`
- **Detail**: The plan said "read `LocaleContextHolder.getLocale()` at the seams" and "expose one
  method returning `AppLanguage`". Built instead with Spring MVC's native `Locale` handler argument
  and `AppLanguage.of(locale)` as the single conversion point — no request-scoped static, and
  deterministic outside a servlet (`LocaleContextHolder` falls back to the JVM default locale, which
  differs between this laptop and Fly). Drift in letter, correct in substance.
- **Fix**: Plan item 3 amended to record the deviation and its reason, so Phases 2–3 follow the shape
  actually built.
- **Decision**: FIXED (plan amended)

### F7 — The 401-on-deleted-account branch was untested

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Safety & Quality
- **Location**: `user/UserSettingsService.java`
- **Detail**: The 0-row branch reaches 401 only because `ApiExceptionHandler`'s inherited MVC type
  list excludes `AuthenticationException` **and** because `ExceptionTranslationFilter` unwraps the
  `ServletException` cause chain. Both are load-bearing and one `@ExceptionHandler(Exception.class)`
  from silently becoming a 500; `CurrentUser.requireId()` rides the same two mechanisms.
- **Fix**: `AuthApiTest.answers401WhenTheAccountWasErasedUnderTheSession` — log in, delete the row,
  PATCH, assert 401 + `application/problem+json`.
- **Decision**: FIXED

### F8 — UserController was doing service work inline

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Architecture
- **Location**: `user/UserController.java`
- **Detail**: `updateCurrentUser` performed a repository write, security-context surgery and audit
  logging in the handler body, against `GoalController`/`ProposalController` which both delegate.
  Both the review agent and the implementer recommended deferring; the author chose to extract now.
- **Fix**: Two collaborators, each owning a rule rather than a step — `user/UserSettingsService`
  (the write, and what an unmatched row means) and `security/AuthenticatedSession` (replacing the
  session's principal, and why Spring Security 6 requires an explicit `saveContext`). The handler is
  now three lines of delegation. `UserController` no longer injects `UserRepository` or
  `SecurityContextRepository`.
- **Decision**: FIXED

### F9 — LocaleConfig comment stronger than the behaviour

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Pattern Consistency
- **Location**: `i18n/LocaleConfig.java:44`
- **Detail**: Setting a default locale removes the JVM-default dependency for an absent or blank
  header, but a header that is present and unparseable still falls through to `request.getLocale()`
  and language-matches the JVM default — that request registers PL on this laptop and EN on Fly. No
  browser sends one.
- **Fix**: Comment corrected to "absent or blank", with the remaining hole named and the reason it is
  not worth closing.
- **Decision**: FIXED

### F10 — Living documentation not walked in this commit

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Plan Adherence
- **Location**: `docs/index.html`
- **Detail**: The new `i18n` package has no `#code-map` row, `#backend` has no `AppLanguage` prose or
  terms entry, the endpoint list lacks `PATCH /api/users/me`, and the "Verified against …" stamp is
  unmoved. The plan explicitly defers the nine-surface walk to Phase 6.
- **Decision**: SKIPPED — owned by Phase 6, whose contract already enumerates all nine surfaces.

## Checked, nothing found

Migration data safety (expand-only nullable `ADD COLUMN`, no rewrite, safe under image rollback);
authorization / IDOR (id taken from the session, no `id` or `email` on `UserUpdate`, CSRF enforced
and pinned); injection (named-parameter JPQL; the only raw SQL is test-only and parameterized);
secret and log leakage (`toString()` still masks the hash; the audit line logs a UUID and an enum);
performance (`GET /api/users/me` still zero queries, login unchanged at one `findByEmail`); the
`equals`/`hashCode` override (verified against `SessionRegistryImpl` 7.1.1 and every `UserPrincipal`
use site — correct, and nothing silently weakened); tab indentation across all changed files.

Sibling sessions keep the old language until re-login. That is a real limitation, documented in both
`AuthenticatedSession.replacePrincipal`'s javadoc and `openapi.yaml`; reaching other sessions would
need a shared session store this deployment deliberately does not have.

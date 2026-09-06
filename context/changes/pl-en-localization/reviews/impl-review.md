<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Polish + English Localization

- **Plan**: `context/changes/pl-en-localization/plan.md`
- **Scope**: Full plan — Phases 1–6 of 6 (Phase 1 re-checked lightly; it was reviewed on its own in
  `impl-review-phase-1.md` and its fixes landed in `f3385ff`)
- **Commits reviewed**: `130f84d` → `e88aab6` (branch head, 86 files vs `master`)
- **Date**: 2026-09-04
- **Reviewers**: two Opus 5 sub-agents (plan drift; safety, quality and pattern compliance), findings
  re-verified by hand against the source before ranking
- **Verdict**: NEEDS ATTENTION → triaged 2026-09-04: 10 fixed, 1 accepted
- **Findings**: 0 critical, 6 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING — one end-state gap (F1), one stale docs line (F5), one hole the plan never covered (F3); nothing MISSING |
| Scope Discipline | WARNING — benign extras only (F10); no "not doing" boundary crossed |
| Safety & Quality | WARNING — no security or data-safety finding; one Neon-idleness regression (F2), one untested Polish plural table (F6) |
| Architecture | PASS |
| Pattern Consistency | WARNING — one contract-guard promise not kept (F4), one Polish label quoted as identity (F7) |
| Success Criteria | PASS — every automated criterion green; manual items honestly left pending |

## Success criteria — evidence

| Check | Result |
|-------|--------|
| `cd backend && mvn test` | **Green on rerun**: 313 run, 0 failures, 0 errors, 5 skipped. The first run (concurrent with the frontend gate and both review agents) died with 148 context-load errors; the `-q` log clipped the first cause and a single class rerun then the full suite both passed cleanly. Treated as environmental and not reproduced. |
| `cd frontend && npm test` | 9 files, 98 tests, all pass |
| `cd frontend && npm run lint` | Clean |
| `cd frontend && npm run build` | Green (`tsc -b` + Vite) |
| `node --test docs/index.test.mjs` | 12/12 |
| 4.3 — removing an `en` key fails the build | **Reproduced**: deleting `layout.pickDomain` from `en.ts` fails `tsc -b` with TS2741; file restored, tree clean |
| Manual items | 1.6 and 1.7 are ticked in the same commit as the code (`130f84d`) with no recorded evidence — see F11. 2.5–6.6 are correctly unticked and remain the production-walk work. |

## Findings

### F1 — Category labels do not follow an in-session language switch

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `frontend/src/layout/AppLayout.tsx:24-31`
- **Detail**: The `/categories` fetch runs in a `[]`-dependency effect, so after the account-menu
  switch the 11 nav labels, the three category `<select>`s in `GoalsPage`, and `DomainPlaceholder`'s
  heading keep the previous language until a reload. The plan's Desired End State says the account
  "reads Polish on every one of those surfaces, without a reload", and manual item 4.5 still expects
  it. The backend side is ready for the refetch: `GET /api/categories` is zero-query and its
  response already carries `Vary: Accept-Language`, so a switch back is served from browser cache.
- **Fix**: Add the resolved language to the effect's dependency list (`i18n.resolvedLanguage`) so
  the switch triggers one refetch.
- **Decision**: FIXED — `AppLayout` refetches `/categories` on `i18n.resolvedLanguage`

### F2 — A language switch re-fires `GET /api/goals`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `frontend/src/pages/GoalsPage.tsx:140-163`
- **Detail**: `load` is a `useCallback` with `[t]` as its dependency and is the dependency of the
  loading effect. react-i18next hands out a new `t` on every language change
  (`useTranslation.js:70-88`, snapshot keyed on `lng`), so each switch recreates `load` and re-runs
  the effect: a real `GET /api/goals`, a real database query, for content that is not localized.
  This is the query class the design was built to avoid (`lessons.md`, "Let a scale-to-zero database
  actually sleep"). Side effect of the same shape: every screen stores an already-translated error
  string in state (`AccountMenu.tsx:53`, `AuthPage.tsx:50`, `ProposalCard.tsx:127`,
  `GoalsPage.tsx:153`), so a banner raised before a switch stays in the old language.
- **Fix**: Take `t` out of `load`'s dependencies — store the error as a catalog key and translate at
  render time (or call the stable `i18n.t`). The same edit makes the error banners follow the
  switch.
- **Decision**: FIXED — `GoalsPage` stores a catalog key and translates at render; `load` no longer depends on `t`

### F3 — The two localized proposal operations do not declare `Accept-Language` in the spec

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `context/foundation/openapi.yaml:512`, `:601` (parameters of `POST /proposals` and
  `POST /proposals/{id}/answer`)
- **Detail**: `ProposalController.propose` and `answer` both take a `Locale` and produce different
  prose per header, but only `POST /users` (`:84`) and `GET /categories` (`:289`) reference the
  `AcceptLanguage` parameter. The parameter's own description says it is read by "the operations
  that generate text for a person", which is exactly these two. Phase 3 of the plan has no
  specification item at all, so this is a plan hole rather than a deviation, but CLAUDE.md's
  "spec + Java + TS in one commit" rule applies and `ApiSurfaceTest` compares operations, not
  parameters, so nothing goes red.
- **Fix**: Add the `$ref: '#/components/parameters/AcceptLanguage'` line to both `parameters:`
  blocks, and add a one-line spec item to Phase 3 of the plan as an addendum.
- **Decision**: FIXED — `AcceptLanguage` referenced on both proposal operations, spec `1.7.1`, Phase 3 addendum in the plan

### F4 — The SPA's `PL`/`EN` copy never joined the contract guard its javadoc promised

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: `backend/src/test/java/com/thedariusz/todoai/AuthApiTest.java:315-316` (javadoc)
  vs `:319-331` (assertion); `frontend/src/auth/auth-context.ts:10`; `frontend/src/auth/AccountMenu.tsx:36`
- **Detail**: The javadoc of `publishesTheLanguageLiteralsTheContractAnchors` says "the SPA's copy
  is the third and joins this assertion in Phase 4, when the catalog gives it one". Phase 4 shipped
  in `ca68446` and the test still compares only `openapi.yaml` against `AppLanguage.values()`. The
  SPA hardcodes `'PL' | 'EN'` in the `User` type and the `'pl' ? 'PL' : 'EN'` mapping in the menu,
  and `AccountMenu.test.tsx:44` asserts `{ language: 'PL' }` against its own copy. That is the
  "both suites green while the two sides disagree" shape from `lessons.md`, whose rule is that an
  unguarded duplication must at least be named as unguarded where it is introduced. A javadoc
  naming a plan is not naming a status.
- **Fix A ⭐ Recommended**: Extend the guard — read `auth-context.ts` from the test and assert it
  contains each `AppLanguage.name()`, the way the sibling at `:299-301` already reads
  `AccountMenu.tsx` for the re-auth URN.
  - Strength: Closes the drift class with the exact pattern already in the file; a rename on any
    side goes red.
  - Tradeoff: A substring check cannot see a value *deleted* from the SPA type; the set comparison
    the spec side gets is not available for a TS union without parsing.
  - Confidence: HIGH — the sibling assertion is three lines and has been stable since DEV-31.
  - Blind spot: `AccountMenu.tsx:36` carries a second copy of the mapping; the test should name both
    files or the mapping should be derived from the type.
- **Fix B**: Rewrite the javadoc to state the SPA copy is unguarded and why.
  - Strength: Honest in one line; no test surface added.
  - Tradeoff: The drift stays possible; the lesson's minimum bar is met, not its intent.
  - Confidence: MEDIUM — acceptable for a two-value enum, but the same argument was made for the
    category codes before six of eleven rotted.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — `AuthApiTest` reads `auth-context.ts` and `AccountMenu.tsx`; proven red on a renamed literal

### F5 — Login sequence diagram still shows a three-component `UserPrincipal`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `docs/index.html:1607`
- **Detail**: The `#flows` "Login (session creation)" diagram reads
  `UserPrincipal (userId, email, passwordHash)`. The record has had a fourth component since
  `130f84d`; the class diagram (`:700-706`) and the glossary row (`:2203`) both say four. Phase 6
  item 1 lists `#flows` among the nine surfaces to walk, and this is the "prose that quietly became
  false" case the plan's own Phase 6 overview warns about. The other eight surfaces were verified
  current, including the new "Choosing a language" flow and the `Verified against DEV-49` stamp.
- **Fix**: Append `, language` to the arrow label on line 1607.
- **Decision**: FIXED — login diagram carries `language`

### F6 — The Polish plural table has no test coverage

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `frontend/src/i18n/pl.ts:107-110`; `frontend/src/test/setup.ts:15`;
  `frontend/src/pages/GoalsPage.test.tsx:865-908`
- **Detail**: `remindIn_one/_few/_many/_other` exists precisely to stop a future one-day preset from
  rendering wrong Polish, but the suite forces English in `beforeEach` and the one Polish walk never
  presses the remind-later button, so the term buttons are never rendered in Polish. A misspelled
  `remindIn_many` would fall through to `_other` and render "Za 7 dnia" with every suite green. The
  forms themselves were checked against `Intl.PluralRules('pl')` and are correct (7/30/90 → many,
  2–4 → few, 1 → one).
- **Fix**: In the existing Polish walk, press the remind-later control and assert `Za 7 dni`,
  `Za 30 dni`, `Za 90 dni`.
- **Decision**: FIXED — the Polish walk presses remind-later and asserts the three terms; proven red on a wrong `many` form

### F7 — A Polish fallback sentence is quoted as the identity of a behaviour

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalPrompt.java:118`
- **Detail**: The javadoc says `ProposalTemplate` "already reads zero this way ("termin już
  minął")". Pre-existing text, but this slice is exactly the event CLAUDE.md warns about: there are
  now two fallback implementations and the comment names only the Polish sentence
  (`ProposalTemplateEn.java:37` says "its deadline has passed" for the same case).
- **Fix**: Name the behaviour ("reads zero as the term having passed, not as no elapsed silence")
  and drop the quoted string.
- **Decision**: FIXED — javadoc names the behaviour and both template classes

### F8 — Two comments claim more than the code does

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `frontend/eslint.config.js:8-10`; `frontend/src/i18n/index.ts:44-45`
- **Detail**: (a) The lint rule is sound — it covers `Literal`, `TemplateElement` and `JSXText`, and
  its exemptions leak into no component — but its header calls anything non-ASCII "copy in
  disguise", which over-claims: a diacritic-free Polish literal (`'Anuluj'`) and, more likely now,
  a hardcoded English literal pass cleanly. (b) The `<html lang>` sync comment says it drives the
  display format of `<input type="date">`; Chrome and Firefox format that input from the browser
  locale, not the document language. The sync is still right for screen readers, and nothing
  depends on the second claim.
- **Fix**: Soften both comments to what the code actually guarantees.
- **Decision**: FIXED — both comments narrowed to what the code guarantees

### F9 — The no-op PATCH guard rests on an optional field

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `frontend/src/auth/auth-context.ts:10`; `frontend/src/auth/AccountMenu.tsx:27`
- **Detail**: `User.language` is typed optional and the menu seeds its state from it. If `/users/me`
  ever answered without `language`, `chosen === language` would never be true and every pick,
  including a re-pick of the current value, would issue the PATCH the guard exists to prevent.
  `openapi.yaml:734` marks the field required, so this is defensive only.
- **Fix**: Make `language` required on the `User` type to match the spec.
- **Decision**: FIXED — `User.language` required; three typed fixtures carry it

### F10 — Previous-slice documentation and shaping artifacts ride on this branch

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: commit `e101a57` — `context/foundation/shape-notes.md`,
  `context/foundation/archive/shape-notes-2026-09-03-2227.md`, `docs/index.html` DEV-48 paragraph
- **Detail**: No plan item names these files. The shape-notes rewrite and archive are the upstream
  `/10x-shape` output for DEV-49, and the `docs/index.html` change in that commit rewrites the
  DEV-48 production-walk record. All benign and all documentation; noted so a reviewer of the PR is
  not surprised by 690 lines of foundation prose in a localization branch. Every "What We're NOT
  Doing" boundary was checked and none is crossed (`ProposalRhythm`, `ApiExceptionHandler`,
  `ProposalEmail`, `LifeDomain` are all untouched).
- **Fix**: None required; mention it in the PR description.
- **Decision**: ACCEPTED — documentation only; to be mentioned in the PR description

### F11 — Phase 1 manual items were ticked without recorded evidence

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `context/changes/pl-en-localization/plan.md:773-774`
- **Detail**: Items 1.6 and 1.7 (register from an English browser; switch and reload) are `[x]` in
  the same commit that introduced the code, and neither the commit nor the Phase 1 review records
  the walk. Every later manual item (2.5–6.6) is honestly `[ ]`. Both Phase 1 behaviours are
  covered by `AuthApiTest`, so the risk is bookkeeping, not correctness.
- **Fix**: Either note the date of the by-hand check beside them or untick them and fold them into
  the Phase 6 production walk.
- **Decision**: FIXED — 1.6 and 1.7 unticked and folded into the Phase 6 production walk

## Checked and clean

Security: no `dangerouslySetInnerHTML`, `innerHTML` or `<Trans>` anywhere, so `escapeValue: false`
is safe; `PATCH /api/users/me` is authenticated and CSRF-protected, both pinned; `Accept-Language`
is narrowed to the enum before it reaches the prompt (constant `englishName()`) or SQL (JPQL
parameter); prompt fencing survived the refactor; no secrets. Performance: `GET /users/me` and
`GET /categories` stay zero-query; the boot reconcile never writes (asserted); the account-menu
no-op guard is real and tested. Reliability: PATCH failure leaves i18next unchanged; the principal
refresh writes through the chain's `SecurityContextRepository`; `AppLanguage.of` handles null,
`Locale.ROOT`, `en-GB`, `pl-PL`, `de`; quality-ordered headers pinned. Data safety: V10 and V11
nullable, no `DEFAULT`, expand-only; V11 touches only the 11 reference rows; column lengths match
the mappings. i18n hazards: all three from the plan resolved (whole-sentence error keys, a real
plural key, `$t(goals.filters.withdrawn)` nesting in both catalogs); `en: typeof pl` plus
`i18next.d.ts` makes a missing key a build error (reproduced). Tests: no frontend file lost
assertions in the migration; the `proposeScheduled` pin really uses an `EN` account.
`ProposalTemplate` and `ProposalTemplateTest` are byte-identical to `master` apart from one javadoc
cross-reference. Both ER SVGs carry `color-scheme: light`.

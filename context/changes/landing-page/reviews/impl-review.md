<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Public landing page

- **Plan**: context/changes/landing-page/plan.md
- **Scope**: Phases 1–2 of 2
- **Date**: 2026-10-14
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Fresh runs during review: frontend tests 147/147, lint, build, `node --test docs/index.test.mjs` 12/12, `git diff --check`. Backend not re-run (no backend files in the range; green in the phase-2 `/check`).

## Findings

### F1 — Small landing text below WCAG AA contrast

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence / Safety & Quality
- **Location**: frontend/src/index.css (`.landing` tokens)
- **Detail**: `--muted: #8a8682` gave 3.4–3.6:1 on the 12px caption and 11px preview labels (needs 4.5:1); the unpressed PL/EN button was 4.47:1 on `--track: #f1ede9`. The plan named `#6f6c69` as the muted colour.
- **Fix**: `--muted: #6f6c69` (4.9:1 on cream), `--track: #f4f1ee` (4.6:1).
- **Decision**: FIXED

### F2 — FAQ questions are read aloud with "plus"/"minus"

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/index.css (`.landing-faq summary::after`)
- **Detail**: Generated `+`/`−` content joined the summary's accessible name in Chromium/Safari.
- **Fix**: `content: '+' / '';` and `content: '−' / '';`
- **Decision**: FIXED

### F3 — The "every token is redeclared" comment isn't true for --accent

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/index.css (`.landing`)
- **Detail**: `.cta` and focus rings read `--accent`, which `.landing` did not redeclare.
- **Fix**: Add `--accent: #863bff;` to `.landing`.
- **Decision**: FIXED

### F4 — Sign-in with no saved destination now redirects twice

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/src/pages/AuthPage.tsx:51
- **Detail**: `navigate(state?.from ?? '/')` now reached the public landing, which then redirected to `/goals`.
- **Fix**: Default to `'/goals'`; the AuthPage test's stand-in app route moved from `/` to `/goals`.
- **Decision**: FIXED

### F5 — Landing header logo has no icon, unlike the app header

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/src/pages/LandingPage.tsx:15
- **Detail**: The app header renders the wordmark with its SVG mark; the landing rendered text only.
- **Fix**: Extracted `frontend/src/layout/Wordmark.tsx`, used by `AppLayout` and `LandingPage`.
- **Decision**: FIXED

### F6 — The preview copies the real app's headings

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/pages/LandingPage.tsx:31-41
- **Detail**: The preview's h2 "Tasks, goals and dreams" and four h3s appear in screen-reader heading navigation; App.test's goals-heading lookup is disambiguated only by its location assertion.
- **Fix**: Accept as an intentional mirror of the real screen (outline valid h1→h2→h3).
- **Decision**: ACCEPTED

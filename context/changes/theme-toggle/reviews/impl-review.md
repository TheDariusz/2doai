<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Theme toggle (light / dark / auto)

- **Plan**: context/changes/theme-toggle/plan.md
- **Scope**: Phases 1–2 of 2
- **Date**: 2026-10-14
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Status bar starts white for dark users

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/index.html:7-13, 22
- **Detail**: The first-paint script set `data-theme` but not the `theme-color` meta. That meta was a fixed `#ffffff` until `theme.ts` loaded. Before this change, a dark-OS user's bar started dark (two metas keyed on the media query).
- **Fix**: Have the inline script set the meta to `#1e1f27` when it resolves to dark.
- **Decision**: FIXED. The meta moved above the script (after `<meta charset>`), and the script sets it in dark. The script still runs before Vite's injected stylesheet and module.

### F2 — Chip restyle shipped without being in the plan

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: frontend/src/index.css:723-728, 827-836
- **Detail**: Phase 1 (aabad5c) changed every `.chip` from the neutral `--chip` surface to an accent tint, with proposal-card overrides. The commit message mentions it; the plan's Changes Required does not.
- **Fix**: Record it in a plan addendum as accepted scope.
- **Decision**: FIXED. The plan's `## Addendum (post-implementation)` records the chip restyle and the approved header deviation (`ThemeCycle`).

### F3 — Inline script would break under a future CSP

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/index.html:6-9
- **Detail**: There is no CSP today. A later `script-src 'self'` policy would silently block the first-paint script and bring back the light flash.
- **Fix**: Note the sha256 requirement in the script's comment.
- **Decision**: FIXED

### F4 — Header button's new state may go unannounced

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/ThemeSwitch.tsx:44-60
- **Detail**: `ThemeCycle`'s accessible name changes on each press. Some screen readers don't re-announce the focused element's new name.
- **Fix**: Accept as is, or add an `aria-live="polite"` echo.
- **Decision**: SKIPPED

### F5 — Landing status bar stays dark over the light page

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/src/theme.ts:44
- **Detail**: In dark, `theme-color` is `#1e1f27` even on the light-pinned landing page. This predates the change for dark-OS users.
- **Fix**: Accept: it predates the change, and the landing page has no install path.
- **Decision**: SKIPPED

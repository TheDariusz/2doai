<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Landing page theme switch

- **Plan**: context/changes/landing-theme-toggle/plan.md
- **Scope**: Phases 1–2 of 2
- **Date**: 2026-10-24
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Automated criteria re-run on Node 22: `LandingPage.test.tsx` 8/8, `npm test` 162/162, lint, build, `node --test docs/index.test.mjs` 12/12. Manual rows 1.3–1.6 and 2.3–2.6 confirmed by the user.

## Findings

### F1 — Dark --muted fails AA on the landing's small text

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: frontend/src/index.css:997 (used at :1156, :1171, :1337)
- **Detail**: Dark `--muted` #7c7887 measures 3.82:1 behind the preview caption (12px on `--bg`), 3.64:1 behind the preview row labels (11px caps on `--cream`) and 4.17:1 behind the footer credit (14px on `--page`), all under 4.5:1. The light landing sets `--muted` equal to `--text` (4.89:1 on cream), so dark regresses against the page's own light design. The value is the app's dark `--muted`, so the app shares the weakness.
- **Fix A ⭐ Recommended**: In the dark landing block, set `--muted` to the dark `--text` #9ca3af, as the light block does.
  - Strength: Mirrors the light landing's muted = text choice; ≥6.15:1 on every landing surface; stays inside this slice.
  - Tradeoff: Caption, labels and credit lose their muted step in dark, already true in light.
  - Confidence: HIGH — ratios measured; same pattern as the light block.
  - Blind spot: The app's dark `--muted` keeps the same AA gap elsewhere.
- **Fix B**: Raise `--muted` in `:root[data-theme='dark']` (~#9089a0) app-wide.
  - Strength: Fixes the gap everywhere; the landing inherits it.
  - Tradeoff: Touches every dark app screen, beyond this slice.
  - Confidence: MED — needs a visual pass over the app's muted text.
  - Blind spot: Non-landing uses of `--muted` unchecked.
- **Decision**: FIXED — Fix A (dark landing `--muted` takes `--text`)

### F2 — Dark landing block copies the app's dark tokens by hand

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: frontend/src/index.css:992-1007
- **Detail**: 12 of the 13 dark landing tokens repeat `:root[data-theme='dark']` (lines 42-62) value for value; only `--cream` is new. The block exists only because the unconditional light block on `body:has(.landing)` hides the inherited dark tokens. Nothing enforces the comment's "the login link one click away changes nothing"; retuning an app dark token silently splits the landing, and `css: false` means no test notices.
- **Fix A ⭐ Recommended**: Scope the light block to `:root:not([data-theme='dark']) body:has(.landing)` and cut the dark block to `--cream` (plus `--muted` under F1 Fix A).
  - Strength: The landing inherits the app's dark palette by construction; F3 and F4 go away with it.
  - Tradeoff: Leaves the plan's "redeclare every token" contract for its intent; slightly longer light selector.
  - Confidence: HIGH — `data-theme` is always resolved to light or dark, and its absence (JS off) still selects light.
  - Blind spot: Needs a dark and light look in the browser after the edit.
- **Fix B**: Keep the copy and add a comment tying it to lines 42-62.
  - Strength: Matches the plan's contract to the letter.
  - Tradeoff: The drift risk stays; a comment is not a guard.
  - Confidence: HIGH — trivial edit.
  - Blind spot: None significant.
- **Decision**: FIXED — Fix A (light block keyed on `:root:not([data-theme='dark'])`; dark block is `--cream` and `--muted: var(--text)` only; verified computed tokens in both schemes)

### F3 — Dark block omits --accent

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/src/index.css:992
- **Detail**: The plan's contract says the dark block redeclares every token the light block declares; `--accent` is missing. No visible effect: the light value #863bff is also the app's dark value.
- **Fix**: Add `--accent: #863bff;` to the dark block (moot under F2 Fix A).
- **Decision**: FIXED — via F2 Fix A (the dark block no longer redeclares the palette)

### F4 — --bg / CHROME comment overstates the status-bar match

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: frontend/src/index.css:994
- **Detail**: The comment says `--bg` equals `CHROME.dark` "so the status bar matches the page", but the landing paints `--page` (#16171d), not `--bg` (#1e1f27), so in dark the status bar is a step lighter than the page. The app has the same gap; the plan's premise was loose and the comment repeats it.
- **Fix**: Reword the comment to name what `--bg` actually matches, or drop it under F2 Fix A.
- **Decision**: FIXED — via F2 Fix A (the comment went with the copied tokens)

### F5 — Unrelated mvnw.cmd normalisation in the phase 1 commit

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: backend/mvnw.cmd (commit 6f31365)
- **Detail**: Whitespace-only re-store from CRLF to LF in the index. `backend/.gitattributes` pins `*.cmd text eol=crlf`, so checkouts still get CRLF (`git ls-files --eol`: `i/lf w/crlf`). Named in the commit body. Benign, but outside the slice.
- **Fix**: No action; it is the normalisation the attributes file already asks for.
- **Decision**: ACCEPTED — benign, documented normalisation per backend/.gitattributes

### F6 — Local Node 26 turns the frontend suite red

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: frontend/ (no Node pin); .github/workflows/frontend.yml:59
- **Detail**: The shell's default node is Homebrew v26.12.1, whose built-in Web Storage shadows jsdom's, so every test touching `localStorage` fails, including the untouched `ThemeSwitch.test.tsx`. CI pins Node 22 and is unaffected. Not caused by this change, but local `/check` on 26 goes red.
- **Fix**: Pin Node 22 for the frontend (`.nvmrc` + `engines`) as separate maintenance.
- **Decision**: QUEUED — follow-ups/review-fixes.md

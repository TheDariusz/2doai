# Landing page theme switch Implementation Plan

## Overview

Let the public landing page follow the device theme like every other screen, and put the same
light / dark / auto control the auth screens carry into its header. The `theme-toggle` change built
the whole mechanism and deliberately left the landing pinned light; this change removes that
exception and gives the landing's warm palette a dark twin.

## Current State Analysis

- Theme is already attribute-driven end to end: `frontend/src/theme.ts` resolves the stored choice
  (`localStorage['2doai.theme']`, default `auto`) and writes `data-theme` on `<html>`; the inline
  script in `frontend/index.html` does the same before first paint; `:root[data-theme='dark']` in
  `frontend/src/index.css:42-62` carries the app's dark tokens. `ThemeSwitch` (three buttons) and
  `ThemeCycle` (one cycling button) in `frontend/src/ThemeSwitch.tsx` are the two ready-made
  controls, with `layout.theme` / `theme.*` keys in both catalogs.
- The landing opts out in four places, all intentional from `theme-toggle`:
  1. `.landing` redeclares 14 palette tokens and pins `color-scheme: light`
     (`frontend/src/index.css:968-998`), so the dark block on `:root` cannot reach in.
  2. `body:has(.landing)` hardcodes the light page colour `#fefdfc` (`index.css:972`).
  3. The brand-mark swap is guarded by `:not(:has(.landing))` (`index.css:290-296`), so the
     light mark always shows there.
  4. `LandingPage.tsx:18-26` runs an effect that forces the `theme-color` meta to `CHROME.light`
     while the landing is mounted and restores it on unmount.
- The landing header is `wordmark · LanguageSwitch · Sign in` with `.landing-header .seg
  { margin-left: auto }` (`index.css:1019`) pushing the one control right. The auth page already
  shows how two switches sit together: `div.auth-controls` (`AuthPage.tsx:85-88`,
  `index.css:565-569`).
- The app header hides the wordmark text at ≤640px (`index.css:911-920`, selector
  `.topbar .wordmark span`) because three controls do not fit a 360px row otherwise.
- `docs/index.html` states the landing stays light in the overview row (line 214), the
  `theme-toggle` verified note (line 304) and the frontend prose; `Wordmark.tsx` and the landing
  comment block in `index.css` say the same.
- Tests: `LandingPage.test.tsx` renders in both locales and asserts no fetch and no pretend
  controls; `AuthPage.test.tsx:51` and `ThemeSwitch.test.tsx` show the `group` / `aria-pressed`
  assertions to copy. `vite.config.ts` sets `css: false` for Vitest, so tests assert attributes and
  storage, never colours.

## Desired End State

- `/` renders in the scheme `data-theme` names: light tokens as today, dark tokens that match the
  app's dark look, with the cream preview card and the author mark drawn on a warm dark surface.
- A visitor with nothing stored sees the landing in their OS scheme, with no light flash on load.
- The landing header shows PL / EN and the three-button theme group side by side; a pick applies
  at once, survives reload, and is what the login screen and the app then show.
- The dark brand mark shows on a dark landing; the installed-PWA status bar follows the theme in
  force like every other page.
- At 360px, in both languages, the header fits without horizontal scroll.

Verify: `npm test`, `npm run lint`, `npm run build`, `node --test docs/index.test.mjs` green;
manual checks listed per phase.

### Key Discoveries:

- Nothing new has to be invented: the slice is one CSS block, four deletions and one mount.
- The landing's `--bg` is `#fff` in light and must be `#1e1f27` in dark so the status bar colour
  `theme.ts` writes (`CHROME`) matches the page without landing-specific logic.
- Every landing rule that paints a surface reads a token (`.preview-row`, `.author-mark rect`,
  `.landing-faq details`, the `.preview-suggestion` gradient sits on `var(--bg)`), so a dark token
  block re-themes the page without touching the rules themselves. The exceptions are the CTA
  (`color: #fff`, hover `#7529f0`) and the author mark's red dot, both of which are correct in
  either scheme because `--accent` is the same purple in both.
- `body` paints `var(--page)` from `:root`, but the landing sets `--page` on `.landing`, which body
  cannot see — hence the hardcoded `body:has(.landing)` colour. Declaring the landing tokens on
  `body:has(.landing)` instead of `.landing` removes that rule entirely.

## What We're NOT Doing

- No change to `theme.ts`, `index.html`, `ThemeSwitch.tsx` or the i18n catalogs; the mechanism
  and the labels exist.
- No landing-specific default: `auto` means "follow the OS" here as everywhere.
- No redesign of the light landing — its tokens stay byte-for-byte.
- No new dark mark asset; the existing `mark-dark.png` is the one the app header uses.
- No `ThemeCycle` on the landing; the three-button group is the pick (see brief).

## Implementation Approach

Phase 1 makes the page itself theme-aware and removes the four guards, verifiable by picking dark
on `/login` and returning to `/`. Phase 2 adds the control to the header, settles the phone-width
layout, updates the tests that describe the header, and walks the living documentation.

## Critical Implementation Details

**User experience spec** — the 360px header is the one open layout question. Hiding the wordmark
text (as the app header does) and tightening the gap should fit `mark · PL EN · ☀ ☾ ◐ · Sign in`;
Polish "Zaloguj się" is the long case. Give `.landing-header` `flex-wrap: wrap` so an overflow
drops the link to a second line instead of forcing a horizontal scroll. **Decided on the prototype
(2026-10-23):** the wrap is accepted — at 360px the sign-in link sits on a second header line in
both languages, and the three-button group stays.

## Phase 1: Dark landing palette and un-pinning

### Overview

Make the landing read `data-theme` like every other page: a dark token block, and the four
light-only guards removed.

### Changes Required:

#### 1. Landing tokens, both schemes

**File**: `frontend/src/index.css` (`body:has(.landing)` and `.landing`, lines 968-998)

**Intent**: Keep the 14 light tokens exactly as they are, drop `color-scheme: light`, and add a
dark block keyed on `:root[data-theme='dark']` so the landing re-themes with the attribute.
Move the token declarations from `.landing` to `body:has(.landing)` so `body { background:
var(--page) }` picks the landing's page colour up in either scheme and the hardcoded `#fefdfc`
rule goes away. Rewrite the comment block above: it currently explains why the page is pinned light.

**Contract**: `:root[data-theme='dark'] body:has(.landing)` redeclares every token the light block
declares. Start from the app's dark values (`index.css:42-62`): `--page #16171d`, `--bg #1e1f27`
(must equal `CHROME.dark`), `--border #2e303a`, `--muted #7c7887`, `--text #9ca3af`,
`--text-h #f3f4f6`, `--accent-bg / --accent-border / --accent-text` as the app's dark purple,
`--track` and `--chip #262730`, the app's dark `--shadow`. `--cream` is the one token with no app
counterpart: a warm dark surface a step above `--page` (around `#26222a`, tuned in the browser),
so the preview card still reads as a card. `color-scheme` on the landing is inherited from `:root`,
which already flips per attribute.

#### 2. Brand-mark swap guard

**File**: `frontend/src/index.css:290-296`, `frontend/src/layout/Wordmark.tsx` (comment)

**Intent**: Remove `:not(:has(.landing))` from both mark rules so the dark mark shows wherever the
page is dark; fix the `Wordmark` javadoc that says the landing pins light.

**Contract**: `:root[data-theme='dark'] .mark-light { display: none }` and the matching
`.mark-dark { display: block }`.

#### 3. Status-bar effect

**File**: `frontend/src/pages/LandingPage.tsx:7,18-26`

**Intent**: Delete the `useEffect` that pins `theme-color` to `CHROME.light` and the `CHROME` /
`useEffect` imports; `theme.ts` already keeps the meta in step with `data-theme` on every page.

**Contract**: `LandingPage` touches no DOM outside its own tree.

#### 4. Test

**File**: `frontend/src/pages/LandingPage.test.tsx`

**Intent**: One test that the landing leaves the status-bar meta alone: create a
`meta[name="theme-color"]` holding `CHROME.dark`, render, assert it still holds `CHROME.dark`, and
that it is unchanged after unmount. Write it first; it fails against the current effect.

### Success Criteria:

#### Automated Verification:

- `npx vitest run src/pages/LandingPage.test.tsx` passes
- `npm test`, `npm run lint`, `npm run build` pass

#### Manual Verification:

- `npm run build && npm run preview`, OS dark, nothing stored: `/` paints dark with no light flash
- Pick dark on `/login`, return to `/`: dark landing, dark mark, cream card reads as a card, author mark and FAQ borders visible, CTA legible
- Pick light on `/login`, return to `/`: the landing is pixel-identical to today
- Installed PWA on a dark landing: status bar is `#1e1f27`

**Implementation Note**: After this phase and its automated checks, pause for the manual palette
check before adding the header control — the `--cream` value is the one thing meant to be tuned by
eye.

---

## Phase 2: Header control, phone-width layout, docs

### Overview

Put the three-button theme group next to the language switch in the landing header, make the row
fit a phone, and describe the result in the living documentation.

### Changes Required:

#### 1. Mount

**File**: `frontend/src/pages/LandingPage.tsx` (header, lines 31-35)

**Intent**: Wrap `LanguageSwitch` and a new `<ThemeSwitch />` in `div.landing-controls`, keeping
the sign-in link last. Same pairing as `AuthPage.tsx:85-88`.

**Contract**: Header order is wordmark, controls group, sign-in link. The theme group is named by
`layout.theme` (`Theme` / `Motyw`) as on the auth screens.

#### 2. Header layout

**File**: `frontend/src/index.css` (`.landing-header`, `.landing-header .seg`, lines 1012-1021;
the `≤640px` visually-hidden list at lines 911-920)

**Intent**: Replace `.landing-header .seg { margin-left: auto }` with `.landing-controls { display:
flex; gap: 10px; margin-left: auto }`. At ≤640px add `.landing-header .wordmark span` to the
visually-hidden selector list (the app header's rule) and tighten `.landing-header` gap. Add
`flex-wrap: wrap` to `.landing-header` as the overflow fallback described in Critical
Implementation Details.

**Contract**: At 360px, both languages, no horizontal scroll; the three theme buttons keep the
`.seg button:has(svg)` sizing.

#### 3. Tests

**File**: `frontend/src/pages/LandingPage.test.tsx`

**Intent**: Extend the bilingual `it.each` with the theme group's name (`Theme` / `Motyw`) and
assert `getByRole('group', { name })` is in the banner. Add one interaction test in the shape of
the existing "keeps the local language … without a settings write" case: click `Dark`, assert
`aria-pressed`, `document.documentElement.dataset.theme === 'dark'`,
`localStorage['2doai.theme'] === 'dark'`, and `fetch` never called.

#### 4. Living documentation

**File**: `docs/index.html`

**Intent**: Walk the CLAUDE.md list. Concretely: the `#overview` "Frontend auth and shell" row
(line 214) — the theme choice is now on the landing too, and the "Public landing page" row
(line 215) gains the switch; the frontend prose that describes which screens carry the theme
control (around lines 231 and 261); the `frontend/src/i18n` code-map row already says the landing
shares the language switch and needs no change, the `frontend/src` row (line 503) is still true;
add a `Verified against the landing-theme-toggle change on <date>` note after the
`landing-tagline-footer` note. Leave the `theme-toggle` note's "stays light" sentence — the notes
are a dated ledger and the new note supersedes it. No backend package, flow, table or endpoint
moved, so the rest of the list is a read-through.

### Success Criteria:

#### Automated Verification:

- `npm test`, `npm run lint`, `npm run build` pass
- `node --test docs/index.test.mjs` passes

#### Manual Verification:

- 360px landing header, PL and EN, light and dark: fits without horizontal scroll, controls reachable
- Pick dark on `/`, follow "Sign in": login screen is dark with `Dark` pressed; log in: header's cycle button shows the moon; reload keeps dark
- Pick auto on `/`, flip the OS: the open landing re-themes
- `docs/index.html` opened in a browser: the overview and verified notes read correctly

---

## Testing Strategy

### Unit Tests:

- `LandingPage.test.tsx`: the status-bar meta is left alone (Phase 1); the theme group is present
  and named in both locales, and a pick writes the attribute and storage with no network call
  (Phase 2).

### Integration Tests:

- Existing `ThemeSwitch.test.tsx`, `AuthPage.test.tsx` and `AppLayout.test.tsx` stay green; they
  prove the control the landing now mounts.

### Manual Testing Steps:

1. Production preview, OS dark, storage clear: `/` is dark from the first frame.
2. On `/`: light → dark → auto, flipping the OS under auto; each step re-themes.
3. Navigate `/` → `/login` → app header → back to `/` with dark stored: dark everywhere, dark mark.
4. 360px, PL and EN, both schemes: header fit, FAQ, footer and author mark.
5. Installed PWA, dark landing: status bar colour.

## Performance Considerations

None. One more token block in the stylesheet; no script change.

## Migration Notes

None. A dark-OS visitor with nothing stored now sees the dark landing; a stored choice already
applies the same way on every other page.

## References

- Mechanism: `frontend/src/theme.ts`, `frontend/index.html`, `frontend/src/ThemeSwitch.tsx`
- Prior decision this reverses: `context/changes/theme-toggle/plan.md` ("What We're NOT Doing")
- Landing design: `context/changes/landing-page/plan-brief.md`
- Change: `context/changes/landing-theme-toggle/change.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Dark landing palette and un-pinning

#### Automated

- [x] 1.1 `npx vitest run src/pages/LandingPage.test.tsx` passes
- [x] 1.2 `npm test`, `npm run lint`, `npm run build` pass

#### Manual

- [x] 1.3 OS dark, nothing stored: `/` paints dark with no light flash
- [x] 1.4 Dark picked on `/login`, back on `/`: dark landing, dark mark, card, author mark, FAQ borders, CTA legible
- [x] 1.5 Light picked: landing identical to today
- [x] 1.6 Installed PWA on a dark landing: status bar `#1e1f27`

### Phase 2: Header control, phone-width layout, docs

#### Automated

- [ ] 2.1 `npm test`, `npm run lint`, `npm run build` pass
- [ ] 2.2 `node --test docs/index.test.mjs` passes

#### Manual

- [ ] 2.3 360px header, PL and EN, light and dark: no horizontal scroll
- [ ] 2.4 Dark picked on `/` carries through login, header and reload
- [ ] 2.5 Auto on `/` follows an OS flip
- [ ] 2.6 `docs/index.html` reads correctly in a browser

# Theme toggle (light / dark / auto) Implementation Plan

## Overview

Let the user pick light, dark or auto (follow the OS) from the app header and the auth screens. The
choice lives on the device, like the pre-login language, and applies before first paint.

## Current State Analysis

- Dark mode already exists and is OS-driven only: every token sits on `:root` in
  `frontend/src/index.css:1-39` and is overridden under `@media (prefers-color-scheme: dark)` at
  `index.css:41-62`; the brand-mark swap uses the same media query at `index.css:284-292`.
- `frontend/index.html:12-13` carries two `theme-color` metas keyed on the media query, which an
  in-app override cannot move.
- The landing page pins itself light (`.landing { color-scheme: light }`, `index.css:964-980`) and
  the mark swap already excludes it (`:root:not(:has(.landing))`).
- The pattern to copy is the language switch: `frontend/src/i18n/index.ts` (one localStorage key,
  read inside `try` because Safari's "block all cookies" throws on every `localStorage` member,
  evaluated as a side-effect import from `main.tsx`), and `frontend/src/i18n/LanguageSwitch.tsx`
  (a `.seg` segmented group with `aria-pressed`), mounted in `layout/AppLayout.tsx:118` and
  `pages/AuthPage.tsx:86`.
- Icons are hand-drawn stroke paths in `frontend/src/pages/icons.tsx`, all `aria-hidden`; the
  control around them carries the name.
- `AppLayout.test.tsx:201` finds the header's language group by name; adding a second `group` with
  its own name does not break it.

## Desired End State

- Header (after login) and both auth screens show a three-button segmented group: light, dark, auto.
- A pick applies instantly, survives a reload, and in auto the page follows the OS live.
- A dark-OS user who has never picked sees exactly what they see today, with no light flash at load.
- The landing page stays light and shows no switch.
- The installed PWA's status bar colour matches the theme in force.

Verify: `npm test`, `npm run lint`, `npm run build` green; manual checks listed per phase.

### Key Discoveries:

- Production builds extract CSS to a `<link>` in `<head>` while the app is a deferred module script,
  so the first paint can happen before any TS runs. The attribute must be set by an inline script in
  `index.html` or dark users flash light on every load.
- Resolving auto to a concrete `light`/`dark` value before writing it to the DOM means the CSS never
  needs a media query, so the 17-token dark block is not duplicated.
- `vite.config.ts` sets `css: false` for Vitest, so tests assert the attribute and storage, never
  computed colours.

## What We're NOT Doing

- No account-level persistence (no column, no API, no OpenAPI change). Theme is a device trait.
- No dark palette for the landing page; it stays pinned light.
- No icon package; three new stroke icons in `icons.tsx`.
- No change to the segmented-control styling beyond what the new buttons need to show an icon.

## Implementation Approach

Resolve the stored choice to a concrete scheme in one place, write it as `data-theme` on `<html>`,
and let CSS key on the attribute. The inline head script and the TS module share the same three-line
rule; the module owns changes and the OS listener, the script owns the first paint.

## Critical Implementation Details

**Timing & lifecycle** — the inline script in `index.html` must run before the stylesheet `<link>`
Vite injects, i.e. sit at the top of `<head>`. Guard the storage read with `try` for the same reason
`i18n/index.ts` does. In auto, subscribe to `matchMedia('(prefers-color-scheme: dark)')` `change`
and re-apply; drop the subscription when a concrete theme is picked (or keep it and no-op when the
stored choice is not auto, whichever is fewer lines).

## Phase 1: Theme resolution + CSS hook

### Overview

Make the document's theme a function of one stored key, applied before first paint, with the CSS
reading the attribute instead of the OS.

### Changes Required:

#### 1. Theme module

**File**: `frontend/src/theme.ts` (new, sibling of `filters.ts`)

**Intent**: Own the stored choice and the DOM write, as `i18n/index.ts` owns the language.

**Contract**: `THEMES = ['light', 'dark', 'auto'] as const`, `type Theme`, `STORED = '2doai.theme'`.
`readTheme(): Theme` returns the stored value if valid, else `'auto'`; storage read in `try`.
`applyTheme(theme: Theme): void` persists (write in `try`), sets `document.documentElement.dataset.theme`
to the resolved `'light' | 'dark'` (auto → `matchMedia` result), and sets the single `theme-color`
meta's `content` to `#ffffff` or `#1e1f27`. Module side effect: `applyTheme(readTheme())` and a
`matchMedia` `change` listener that re-applies when the stored choice is auto. Imported for side
effect from `main.tsx` right after `./i18n`.

#### 2. First-paint script and meta

**File**: `frontend/index.html`

**Intent**: Set `data-theme` before the stylesheet arrives; collapse the two media-keyed
`theme-color` metas into one the module can drive.

**Contract**: One inline `<script>` as the first child of `<head>`, three statements: read
`localStorage['2doai.theme']` in `try`, resolve `light`/`dark`/else-OS, write
`document.documentElement.dataset.theme`. One `<meta name="theme-color" content="#ffffff">`.
A comment names `theme.ts` as the owner of the same rule.

#### 3. CSS keyed on the attribute

**File**: `frontend/src/index.css`

**Intent**: Replace the OS media query with the attribute the module writes.

**Contract**: `@media (prefers-color-scheme: dark) { :root { … } }` at line 41 becomes
`:root[data-theme='dark'] { … }` with the same tokens. The mark block at line 284 becomes
`:root[data-theme='dark']:not(:has(.landing)) .mark-light { display: none }` and the matching
`.mark-dark { display: block }`. `color-scheme: light dark` on `:root` becomes
`color-scheme: light` with `:root[data-theme='dark'] { color-scheme: dark }` so native widgets follow
the forced theme, not the OS.

#### 4. Module test

**File**: `frontend/src/theme.test.ts` (new)

**Intent**: Pin the resolution rule and the storage guard, like `i18n/index.test.ts`.

**Contract**: Cases: stored `light`/`dark` win over OS; stored `auto`, garbage and nothing fall back
to `matchMedia`; a throwing `localStorage` yields auto without throwing; `applyTheme` writes the
attribute, the storage key and the meta `content`. jsdom lacks `matchMedia`, so stub it with
`vi.stubGlobal` (reset by `unstubGlobals`).

### Success Criteria:

#### Automated Verification:

- `npx vitest run src/theme.test.ts` passes
- `npm test` passes (existing suites unaffected)
- `npm run lint` and `npm run build` pass

#### Manual Verification:

- With nothing stored, a dark-OS browser shows the dark palette with no light flash on a hard reload of the production build (`npm run preview`)
- Setting `localStorage['2doai.theme'] = 'light'` in devtools on a dark OS renders light after reload; the landing stays light either way
- Flipping the OS theme while `auto` is in force re-themes the open tab

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation before proceeding.

---

## Phase 2: ThemeSwitch control + mounts

### Overview

Give the user the control, in the two places the language switch already lives, and walk the docs.

### Changes Required:

#### 1. Icons

**File**: `frontend/src/pages/icons.tsx`

**Intent**: Three decorations for the three buttons: `SunIcon`, `MoonIcon`, `AutoThemeIcon`
(a half-filled circle). Same `Icon` wrapper, `aria-hidden`, size 16.

**Contract**: Three exported components, no props.

#### 2. ThemeSwitch

**File**: `frontend/src/ThemeSwitch.tsx` (new, next to `theme.ts`)

**Intent**: The `LanguageSwitch` shape over `THEMES`: a `.seg` group named `t('layout.theme')`, each
button `aria-label={t(\`theme.${theme}\`)}`, `aria-pressed` on the current choice, icon as content.

**Contract**: No props; it reads `readTheme()` into `useState` and calls `applyTheme` on click. The
header and the auth page never mount at the same time, so no shared subscription is needed.

#### 3. Catalogs

**Files**: `frontend/src/i18n/en.ts`, `frontend/src/i18n/pl.ts`

**Intent**: `layout.theme` ("Theme" / "Motyw") and a `theme` block with `light`, `dark`, `auto`
("Light" / "Jasny", "Dark" / "Ciemny", "Auto" / "Automatyczny"). Both catalogs, same keys, so the
typed `i18next.d.ts` resource stays satisfied.

#### 4. Mounts

**Files**: `frontend/src/layout/AppLayout.tsx:118`, `frontend/src/pages/AuthPage.tsx:86`

**Intent**: `<ThemeSwitch />` immediately after `<LanguageSwitch …/>` in both. On the auth page wrap
the two in a `div.auth-controls` with `display: flex; gap: 10px` so they sit on one row; the header
already has `.topbar-controls`.

#### 5. Segmented-control CSS for icon buttons

**File**: `frontend/src/index.css` (`.seg button`, line 214)

**Intent**: An icon-only button needs `display: inline-flex; align-items: center` and a narrower
padding (`0 8px`) so three of them fit next to "PL EN" on a 360px screen. Check the header at phone
width after.

#### 6. Tests

**Files**: `frontend/src/ThemeSwitch.test.tsx` (new), `frontend/src/layout/AppLayout.test.tsx:201`,
`frontend/src/pages/AuthPage.test.tsx`

**Intent**: The component test clicks "Dark" and asserts `aria-pressed`, `data-theme` and the storage
key; the header test gains one `findByRole('group', { name: 'Theme' })` line; the auth test gains
the same presence check. `localStorage.clear()` in `setup.ts` already resets the key between tests.

#### 7. Living documentation

**File**: `docs/index.html`

**Intent**: Walk the CLAUDE.md list. Concretely: the `frontend/src` code-map row (line 492) gains
"and the device-level theme switch"; the `#overview` capability table gets a one-line row or clause
for the theme; the `Verified against …` note is stamped with this change. No backend package, flow,
table or endpoint moved, so the rest of the list is a read-through, not an edit.
Run `node --test docs/index.test.mjs`.

### Success Criteria:

#### Automated Verification:

- `npm test`, `npm run lint`, `npm run build` pass
- `node --test docs/index.test.mjs` passes

#### Manual Verification:

- Header on a 360px-wide viewport shows wordmark, language, theme, account and logout without wrapping or horizontal scroll
- Picking dark on the login screen, logging in, and reloading keeps dark
- The installed PWA's status bar matches the picked theme
- The landing page shows no theme control and stays light

---

## Testing Strategy

### Unit Tests:

- `theme.test.ts`: resolution order, storage guard, DOM/meta writes.
- `ThemeSwitch.test.tsx`: pressed state and the effect of a click.

### Integration Tests:

- `AppLayout.test.tsx` and `AuthPage.test.tsx`: the control is present and named in both locales.

### Manual Testing Steps:

1. `npm run build && npm run preview`, OS dark, nothing stored: no light flash.
2. Pick light, reload, pick auto, flip OS: each step re-themes.
3. Phone-width header, landing page, installed PWA status bar.

## Performance Considerations

None. One attribute write and one `matchMedia` listener.

## Migration Notes

None. A missing key means auto, which is today's behaviour.

## References

- Pattern: `frontend/src/i18n/index.ts`, `frontend/src/i18n/LanguageSwitch.tsx`
- Change: `context/changes/theme-toggle/change.md`

## Addendum (post-implementation)

- **Header control (Phase 2, approved mid-phase):** the three-button group did not fit a 360px header
  (114px over), so the header carries `ThemeCycle`, one icon button stepping light → dark → auto, and
  hides the "2do AI" wordmark text visually at ≤640px. The auth screens keep `ThemeSwitch` as planned.
- **Chip restyle (reverted in code review):** `.chip` briefly moved to an accent tint with
  proposal-card overrides; it went back to the neutral `--chip` surface as out of scope for this
  slice and belongs in its own change.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Theme resolution + CSS hook

#### Automated

- [x] 1.1 `npx vitest run src/theme.test.ts` passes — aabad5c
- [x] 1.2 `npm test` passes (existing suites unaffected) — aabad5c
- [x] 1.3 `npm run lint` and `npm run build` pass — aabad5c

#### Manual

- [x] 1.4 Dark OS, nothing stored: dark palette, no light flash on the production build — aabad5c
- [x] 1.5 Stored `light` on a dark OS renders light; landing stays light — aabad5c
- [x] 1.6 OS flip while `auto` re-themes the open tab — aabad5c

### Phase 2: ThemeSwitch control + mounts

#### Automated

- [x] 2.1 `npm test`, `npm run lint`, `npm run build` pass — b5a8b93
- [x] 2.2 `node --test docs/index.test.mjs` passes — b5a8b93

#### Manual

- [x] 2.3 360px header fits without wrapping or horizontal scroll — b5a8b93
- [x] 2.4 Dark picked on login survives login and reload — b5a8b93
- [x] 2.5 Installed PWA status bar matches the picked theme — b5a8b93
- [x] 2.6 Landing shows no theme control and stays light — b5a8b93

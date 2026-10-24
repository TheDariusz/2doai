# Landing page theme switch — Plan Brief

> Full plan: `context/changes/landing-theme-toggle/plan.md`

## What & Why

The landing page is the only screen that ignores the theme: it is pinned light and shows no switch,
while the login screen one click away offers light / dark / auto. This change makes the landing
follow the same device-level choice and puts the same control in its header, so a dark-mode visitor
meets the product in the scheme they use.

## Starting Point

`theme-toggle` (2026-10-14) built the mechanism — `data-theme` on `<html>`, set before first paint,
read by every token — and deliberately kept the landing out: its warm "paper" palette had no dark
twin. That exclusion lives in four spots: the landing's own token block with `color-scheme: light`,
a hardcoded body colour, a guard on the brand-mark swap, and an effect that pins the status-bar
colour light while the landing shows.

## Desired End State

`/` renders light or dark with the rest of the app, with no flash on load. The header shows
PL / EN and the three-button theme group side by side; a pick there is what the login screen and
the app then show. The dark landing uses the app's dark surfaces with a warm dark card where the
cream one was, so the jump to the login screen is invisible.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Control in the header | Three-button `ThemeSwitch`, as on the auth screens | A first-time visitor sees the three options; the cycling button was a phone-width compromise for the busy app header |
| Dark palette | App's dark tokens, with a warm dark surface for the cream card | Landing-in-dark and app-in-dark read as one product; the light landing stays byte-for-byte |
| First visit, nothing stored | `auto`, follow the OS | One rule everywhere; the inline first-paint script already does this |
| Mark and status bar | Same as the app: drop the mark guard and the status-bar effect | Remove special cases rather than add them; `--bg` in dark equals `CHROME.dark` so the status bar matches for free |
| Token scope | Declare landing tokens on `body:has(.landing)` | Lets `body` paint the landing's page colour itself and deletes the hardcoded `#fefdfc` rule |

## Scope

**In scope:** dark token block for the landing, removing the four light-only guards, `ThemeSwitch`
in the landing header with a phone-width layout, tests, `docs/index.html`.

**Out of scope:** any change to `theme.ts`, `index.html`, `ThemeSwitch.tsx` or the i18n catalogs;
a landing-specific default; a redesign of the light landing; new assets.

## Architecture / Approach

Nothing new is introduced. The stylesheet gains a `:root[data-theme='dark'] body:has(.landing)`
block mirroring the light one; the four guards go; `LandingPage` mounts the existing control next
to the language switch inside a `.landing-controls` wrapper, and the header hides the wordmark
text at phone width as the app header does.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Dark palette and un-pinning | The landing re-themes with `data-theme`; dark mark; status bar follows | The warm card colour needs tuning by eye |
| 2. Header control, layout, docs | The switch in the header; 360px fit in PL and EN; docs walked | `mark · PL EN · three icons · Zaloguj się` may not fit 360px without wrapping |

**Prerequisites:** none beyond the frontend toolchain; the `theme-toggle` and `landing-page`
changes are merged.
**Estimated effort:** one session, two phases; the phone-width header is the only variable.

## Open Risks & Assumptions

- The 360px header: the plan hides the wordmark text and allows the row to wrap; if the wrap looks
  wrong the fallback choices are a smaller gap, a shorter link label, or `ThemeCycle` on phones only.
- A dark-OS visitor who has never picked now sees the dark landing by default, so the "light paper"
  first impression the `landing-page` change designed for is no longer what most dark-OS visitors
  meet first. Accepted: that is what `auto` means everywhere else.

## Success Criteria (Summary)

- On `/`, a visitor can pick light, dark or auto and the whole page, mark and status bar follow;
  the choice carries into login and the app.
- A dark-OS visitor sees a dark landing from the first frame.
- `npm test`, `npm run lint`, `npm run build` and `node --test docs/index.test.mjs` are green, and
  `docs/index.html` no longer says the landing stays light.

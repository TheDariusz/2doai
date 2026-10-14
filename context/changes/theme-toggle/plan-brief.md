# Theme toggle (light / dark / auto) — Plan Brief

> Full plan: `context/changes/theme-toggle/plan.md`

## What & Why

Let the user choose light, dark or auto (follow the OS) instead of being bound to the OS setting. A
small quality-of-life feature with no backend.

## Starting Point

Dark mode already exists as an OS-driven media query over the `:root` tokens in `index.css`. The
language switch gives the exact pattern to copy: a localStorage-backed choice read before first
render, a `.seg` segmented control, mounted in the header and on the auth screens.

## Desired End State

A three-button group (sun, moon, auto) next to the language switch in the header and on the auth
screens. A pick applies instantly, survives reloads, and auto follows the OS live. Users who never
pick see exactly today's behaviour, with no light flash at load. The landing stays light.

## Key Decisions Made

| Decision        | Choice                                   | Why (1 sentence)                                                        |
| --------------- | ---------------------------------------- | ----------------------------------------------------------------------- |
| Placement       | Header next to language, and auth screens | Copies the LanguageSwitch pattern; always one tap away.                 |
| Persistence     | Device only, one localStorage key         | Theme is a device trait; an account column would be 5x the work.        |
| Landing page    | Stays light, no switch                    | It was designed light-only with its own cream tokens.                   |
| Auto & default  | Auto = OS, default auto, live-follow      | Current users see no change until they opt in.                          |
| CSS mechanism   | JS resolves to `data-theme="light\|dark"` | No media query left in CSS, so the dark token block is not duplicated.  |
| First paint     | Inline head script in `index.html`        | Vite's stylesheet `<link>` paints before the module runs.               |

## Scope

**In scope:** `theme.ts` module, head script, CSS attribute hook, single `theme-color` meta,
`ThemeSwitch` + three icons, PL/EN labels, mounts in header and auth page, tests, docs walk.

**Out of scope:** account-level persistence, landing dark palette, icon packages.

## Architecture / Approach

`index.html` inline script and `theme.ts` share one rule: stored `light`/`dark` wins, anything else
resolves to the OS. The resolved value is written as `data-theme` on `<html>`; `index.css` keys its
dark block on that attribute. `ThemeSwitch` reads and writes through `theme.ts` only.

## Phases at a Glance

| Phase                          | What it delivers                              | Key risk                              |
| ------------------------------ | --------------------------------------------- | ------------------------------------- |
| 1. Resolution + CSS hook       | Theme follows the stored key, no flash        | Head script order vs the stylesheet   |
| 2. Control + mounts            | The switch, labels, tests, docs               | Header width on a 360px phone         |

**Prerequisites:** none.
**Estimated effort:** one session.

## Open Risks & Assumptions

- The same three-line rule lives in the head script and in `theme.ts`; a change to one must reach the other.
- Native widgets (date inputs, scrollbars) follow `color-scheme` on `:root`, which the plan switches per attribute; verify nothing in the landing regresses.

## Success Criteria (Summary)

- Light, dark and auto each render as picked and survive a reload.
- A dark-OS user with no stored choice sees no change and no flash.
- `/check` and the docs test are green.

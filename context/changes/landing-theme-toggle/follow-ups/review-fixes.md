# Review follow-ups: landing-theme-toggle

From `reviews/impl-review.md` (2026-10-24).

## F6 — Pin the frontend to Node 22

Homebrew Node 26 (the shell default here) ships a built-in Web Storage that shadows jsdom's, so
every Vitest test touching `localStorage` fails locally, including untouched ones such as
`ThemeSwitch.test.tsx`. CI pins Node 22 (`.github/workflows/frontend.yml`) and is unaffected, so a
local `/check` goes red while CI is green. Add `frontend/.nvmrc` (22) and an `engines.node` range in
`frontend/package.json`; alternatively run Vitest with `--no-experimental-webstorage`. Separate
maintenance, not part of this slice.

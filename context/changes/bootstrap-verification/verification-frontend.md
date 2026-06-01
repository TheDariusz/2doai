---
bootstrapped_at: 2026-05-28T19:59:04Z
starter_id: vite-react
starter_name: Vite + React
project_name: 2doai-web
language_family: js
package_manager: npm
cwd_strategy: subdir-then-move
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: npm audit --json
---

## Hand-off

Source: `context/foundation/tech-stack-frontend.md` (frontend component of the 2doai monorepo).

```yaml
starter_id: vite-react
package_manager: npm
project_name: 2doai-web
hints:
  language_family: js
  team_size: solo
  deployment_target: cloudflare-pages
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
  path_taken: custom
  quality_override: true
  self_check_answers:
    typed: true
    from_official_starter: true
    conventions: true
    docs_current: true
    can_judge_agent: false
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: true
  has_background_jobs: false
```

**Why this stack** (verbatim from hand-off body):

> Solo developer building the read-only-offline PWA frontend (FR-017) for 2do AI, paired with an already-selected Spring Boot backend (see tech-stack.md) that owns data, auth, and AI orchestration. The recommended full-stack default for `(web, js)` — 10x-astro-starter — was rejected because its bundled Supabase backend would duplicate Spring; this project needs a frontend-only SPA that consumes the Spring REST API. Vite + React + TypeScript wins on the stated preferences: TypeScript end-to-end, mainstream React community, and a rich component-library ecosystem (shadcn/ui, MUI). It ships as static files (no extra server runtime alongside the JVM), deploys to Cloudflare Pages, and supports read-only offline via vite-plugin-pwa. Known tradeoff: Vite + React is not convention-based (no built-in routing/data layer), failing one agent-friendly gate; the user proceeded consciously (quality_override), with the standard compensation being TanStack Router/Query plus a CLAUDE.md that codifies conventions. Auth and AI flags are set for the login and AI-conversation UI; payments and realtime are out of scope per PRD non-goals, and background scheduling lives in the Spring backend. CI on GitHub Actions with auto-deploy-on-merge matches the solo, 7-week, after-hours profile.

## Pre-scaffold verification

| Signal      | Value                                            | Severity | Notes                                  |
| ----------- | ------------------------------------------------ | -------- | -------------------------------------- |
| npm package | create-vite v9.0.7 published 2026-05-11          | fresh    | resolved from cmd_template (`npm create vite`) |
| GitHub repo | not run                                          | —        | card `docs_url` (`vitejs.dev/guide/`) is not a GitHub repo |

create-vite was published ~2.5 weeks before this run — fresh. Proceeded.

## Scaffold log

**Resolved invocation**: `npm create vite@latest .bootstrap-scaffold -- --template react-ts` (run in `frontend/`)
**Strategy**: subdir-then-move
**Exit code**: 0
**Files moved**: 12 (public/, src/, .gitignore, eslint.config.js, index.html, package.json, README.md, tsconfig.json, tsconfig.app.json, tsconfig.node.json, vite.config.ts)
**Conflicts (.scaffold siblings)**: none (`frontend/` was an empty directory)
**.gitignore handling**: moved silently (no pre-existing `.gitignore` in `frontend/`)
**.bootstrap-scaffold cleanup**: deleted

**Post-scaffold install note**: The Vite template scaffolds without installing dependencies (no `&& npm install` in the card's `cmd_template`). To make the project runnable and to enable the dependency audit below, `npm install` was run after the move-up — 152 packages added, 153 audited, exit 0. This is one step beyond what `cmd_template` itself runs; recorded here for audit-trail completeness.

## Post-scaffold audit

**Tool**: `npm audit --json`
**Summary**: 0 CRITICAL, 0 HIGH, 0 MODERATE, 0 LOW
**Direct vs transitive**: 0 of all severities; nothing to break down. Dependency tree: 4 prod, 180 dev, 33 optional (183 total).

Clean tree — no advisories at scaffold time.

## Hints recorded but not acted on

v1 surfaces these hints but takes no automated action on them (deferred to a future agent-context skill):

| Hint                    | Value                |
| ----------------------- | -------------------- |
| bootstrapper_confidence | verified             |
| quality_override        | true                 |
| path_taken              | custom               |
| self_check_answers      | typed:true, from_official_starter:true, conventions:true, docs_current:true, can_judge_agent:false |
| team_size               | solo                 |
| deployment_target       | cloudflare-pages     |
| ci_provider             | github-actions       |
| ci_default_flow         | auto-deploy-on-merge |
| has_auth                | true                 |
| has_payments            | false                |
| has_realtime            | false                |
| has_ai                  | true                 |
| has_background_jobs     | false                |

**`quality_override: true`** — the user knowingly chose Vite + React, which fails the `convention_based` agent-friendly gate (no built-in routing/data layer). v1 surfaces this but does not compensate. Recommended compensation (decided in conversation): add TanStack Router + TanStack Query and codify folder/routing conventions in a CLAUDE.md so an AI agent stays consistent.

**`can_judge_agent: false`** — the load-bearing self-check item was left unmarked. The CLAUDE.md conventions doc is the mitigation: it gives the agent (and the user) an explicit standard to check against.

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` at the monorepo root (if you have not already) to start your own repo history.
- No `.scaffold` siblings were created (the target folder was empty), so there is nothing to reconcile.
- Add `vite-plugin-pwa` to satisfy the read-only-offline requirement (FR-017).
- Add TanStack Router + TanStack Query as the routing/data layer (the convention-based gate compensation).
- Wire `VITE_API_URL` and a Vite dev proxy (`/api` → `http://localhost:8080`) per the deployment decision in `context/foundation/deployment.md`.
- Generate a typed API client from the Spring OpenAPI spec once the backend exposes one.
- The backend component (`tech-stack.md`, Spring Boot) was bootstrapped separately — see `verification.md`.

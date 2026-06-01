---
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
---

## Why this stack

Solo developer building the read-only-offline PWA frontend (FR-017) for 2do AI,
paired with an already-selected Spring Boot backend (see tech-stack.md) that owns
data, auth, and AI orchestration. The recommended full-stack default for
`(web, js)` — 10x-astro-starter — was rejected because its bundled Supabase
backend would duplicate Spring; this project needs a frontend-only SPA that
consumes the Spring REST API. Vite + React + TypeScript wins on the stated
preferences: TypeScript end-to-end, mainstream React community, and a rich
component-library ecosystem (shadcn/ui, MUI). It ships as static files (no extra
server runtime alongside the JVM), deploys to Cloudflare Pages, and supports
read-only offline via vite-plugin-pwa. Known tradeoff: Vite + React is not
convention-based (no built-in routing/data layer), failing one agent-friendly
gate; the user proceeded consciously (quality_override), with the standard
compensation being TanStack Router/Query plus a CLAUDE.md that codifies
conventions. Auth and AI flags are set for the login and AI-conversation UI;
payments and realtime are out of scope per PRD non-goals, and background
scheduling lives in the Spring backend. CI on GitHub Actions with
auto-deploy-on-merge matches the solo, 7-week, after-hours profile.

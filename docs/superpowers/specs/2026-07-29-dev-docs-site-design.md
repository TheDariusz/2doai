# Developer Docs Site — Design

**Date:** 2026-07-29
**Status:** Approved
**Audience:** developers working on 2do AI (a separate user-facing doc will come later)

## Goal

A developer documentation site for the 2do AI repo: architecture, class diagrams, sequence diagrams, glossary, and CI/CD — readable by anyone who cloned the repo.

## Decisions

- **Form:** single self-contained `docs/index.html` SPA. Vanilla JS hash-router (sidebar links show/hide sections). No framework, no build step, no new toolchain.
- **Viewing:** opened directly from the repo (`file://` or any static server). No hosting setup, no GitHub Pages.
- **Diagrams:** Mermaid text embedded in the HTML, rendered client-side by mermaid.js from CDN (internet required to view — acceptable for a dev audience). Data-model diagrams are NOT redrawn: link the existing `context/foundation/data-model-current.svg` and `data-model-target.svg` via relative paths.
- **Scope:** implemented code AND planned/target architecture, always clearly labeled which is which.
- **Maintenance:** hand-edited. Diagrams are diffable Mermaid text. A "how to update these docs" note at the top of the file.

## Sections

1. **Overview** — what 2do AI is; two-project layout (`backend/` Spring Boot 4 / Java 25 / Maven, `frontend/` React 19 + Vite PWA); tech stack table; pointer to `context/foundation/` design docs.
2. **Architecture** — general Mermaid diagram: Browser PWA → Cloudflare Pages → `/api/*` reverse proxy → Fly.io backend → Neon Postgres; connected services (GitHub Actions, Flyway, planned AI provider). Current vs target both shown, labeled.
3. **Backend design** — Mermaid class diagrams per slice: `auth`, `account`, `user`, `session`, `security`, `category`, `ai/memory`, derived from the real code. Data model subsection links the drawio SVGs.
4. **Flows** — Mermaid sequence diagrams, split into two labeled groups:
   - *Implemented:* registration, login (session-cookie issuance), authenticated request, logout, account deletion (FR-019), startup `CategorySyncCheck`.
   - *Planned:* task management, AI orchestration, reminders — derived from `prd.md` / `roadmap.md`.
5. **Glossary** — ubiquitous language: task layers (current / long-term / dreams), 11 life domains, AI memory episode, natural-rhythm reminders, session-model terms, aggregate names.
6. **CI/CD & Deployment** — pipeline diagram + explanation of `.github/workflows/deploy-backend.yml` and `deploy-frontend.yml`; Pattern B unified origin; Flyway expand-only policy; rollback notes (from `deployment-runbook.md`).
7. **Roadmap / Target state** — planned features with target diagrams (including the planned sequence diagrams above), linking to `context/foundation/` for detail.

Note: sequence diagrams live primarily in section 4 (Flows) with an Implemented/Planned split; section 7 links back rather than duplicating.

## Testing / acceptance

- Open `docs/index.html` in a browser: every sidebar link shows its section; every Mermaid diagram renders (a failed diagram shows an error box — none may be present).
- Relative SVG links resolve from `docs/` to `context/foundation/`.
- Verified via the in-app browser before completion.

## Out of scope

User-facing docs, search, versioning, GitHub Pages, offline viewing (CDN dependency accepted).

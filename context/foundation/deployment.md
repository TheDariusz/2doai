# Deployment Architecture

**Decision date:** 2026-05-28
**Status:** decided

## Decision

Deploy the frontend and backend **independently** (separate CI pipelines, separate
hosts), but expose them to the browser under a **single public origin** via a
Cloudflare reverse proxy. This is "Pattern B — decoupled deploys, unified origin."

```
                           2doai.app  (single public origin)
                                │
                     ┌──────────┴───────────┐
         /api/*  →   backend  (Spring Boot on Fly)
         /*      →   frontend (Vite + React static build on Cloudflare Pages)
                     routing handled by Cloudflare
```

## Why Pattern B (over alternatives)

- **Pattern A (separate domains, e.g. app.* + api.*)** — works, but the browser sees
  two origins → CORS config required and cross-site cookie rules complicate auth.
- **Pattern C (backend serves the static frontend, one deployable)** — simplest ops,
  but every frontend change redeploys the JVM and you lose the global CDN.
- **Pattern B (chosen)** — keeps independent deploy cadences AND removes the
  cross-origin tax: no CORS, first-party cookies work, one domain to remember.
  Cloudflare (already our frontend host) can route `/api/*` to Fly natively, so
  this costs essentially nothing to adopt.

## What this means concretely

- **Hosting:** frontend → Cloudflare Pages; backend → Fly. Unchanged from the
  tech-stack hand-offs.
- **Public origin:** one hostname (`2doai.app`); Cloudflare routes
  `/api/*` to the Fly backend, everything else to the Pages static build.
- **CORS:** not needed in production once same-origin routing is in place. (Local
  dev still uses the Vite dev proxy to localhost:8080.)
- **Auth:** same-origin means first-party cookies are viable for FR-001/002;
  bearer-token JWT remains an option. Defer the final choice to implementation.
- **API versioning:** prefix backend routes with `/api/v1/...`.
- **CI:** path-filtered GitHub Actions — `backend/**` deploys to Fly,
  `frontend/**` deploys to Cloudflare Pages, independently.

## Open follow-ups

- Wire the Cloudflare `/api/*` → Fly route at infra-setup time (Cloudflare Pages
  Functions proxy or a Worker / DNS + rules).
- Decide cookie-vs-JWT auth during backend implementation.

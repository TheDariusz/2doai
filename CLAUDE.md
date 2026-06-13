# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

2do AI — a personal AI-powered todo + planning app. Three task layers (current, long-term, dreams) across 11 life domains, with proactive natural-rhythm reminders. Solo dev, after-hours MVP.

**Two independent projects, no monorepo tooling** (no root `package.json`, no workspaces). Build each from its own directory:
- `backend/` — Spring Boot 4.x REST API (Java 25, Maven). Auth, AI orchestration, background jobs.
- `frontend/` — React 19 + Vite + TypeScript PWA that consumes the backend REST API.

Design docs live in `context/foundation/` (prd.md, tech-stack*.md, deployment.md) — read these for product/architecture intent.

## Commands

Backend (run from `backend/`):
- `mvn spring-boot:run` — dev server
- `mvn test` — JUnit 5 + Spring Boot Test
- `mvn test -Dtest=ClassName#methodName` — single test
- `mvn package` — build JAR

Frontend (run from `frontend/`):
- `npm run dev` — Vite dev server (HMR)
- `npm run build` — `tsc` typecheck + Vite build
- `npm run lint` — ESLint (flat config)
- `npm test` — Vitest (single run); `npm run test:watch` for watch mode
- `npx vitest run src/App.test.tsx` — single test file; append `-t "name"` for one test

`/check` runs the full gate (backend tests + frontend test + lint + build) in one shot.

## Conventions

- **TDD — write the failing test first**, then implement. Backend uses JUnit 5; frontend uses Vitest + React Testing Library (jsdom env, globals enabled; matchers from `@testing-library/jest-dom`). Co-locate tests as `*.test.tsx` next to the code; shared setup is `src/test/setup.ts`.
- **Backend base package is `com.thedariusz.todoai`** (groupId `com.thedariusz`, artifactId `todoai`). Never use a digit-leading package like `com.example.2doai` — it's invalid Java.
- Persistence: **PostgreSQL via Spring Data JPA / Hibernate.**
- TypeScript runs strict (`noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`). ESLint flat config in `eslint.config.js`; no Prettier — `eslint --fix` is the formatter.
- Git: project targets GitHub. Use feature branches, conventional commit messages, and PRs to merge.

### Persistence

Project-wide rules every slice inherits (canonical schema diagram: `context/foundation/data-model.md`):

- **Schema is owned by Flyway** — versioned migrations in `backend/src/main/resources/db/migration` (`V<n>__*.sql`), backward-compatible / expand-only (safe under an image rollback; destructive changes follow expand/contract). Boot 4 needs the `spring-boot-flyway` integration module, not just `flyway-core`.
- **Hibernate runs `ddl-auto=validate`** — it never alters the schema, only validates mappings against it (a free drift guard).
- **Domain aggregates use UUID v7 surrogate PKs** via Hibernate `@UuidGenerator` (RFC 9562, `UuidVersion7Strategy` — time-ordered, index-friendly). Applied when the first real entity lands (S-01); not yet used.
- **Every domain table has `created_at` / `updated_at` `timestamptz` audit columns.**
- **Columns are `snake_case`** (Java `namePl` ↔ column `name_pl`).
- **Reference tables may use a stable natural key** instead of a surrogate PK and omit audit columns (e.g. `category.code`, mirrored by the `LifeDomain` enum; a startup `CategorySyncCheck` fails fast on table/enum drift).
- **Postgres 18** across dev (`compose.yaml`), tests (Testcontainers), and prod (Neon) — keep all three on the same major.

## Deployment (Pattern B — unified origin)

- Frontend → Cloudflare Pages (static build); Backend → Fly.io (JVM).
- Single public origin: Cloudflare reverse-proxies `/api/*` to the Fly backend, so **production is same-origin (no CORS needed)**. For local dev, proxy `/api` to the backend via Vite.
- Auth (cookie vs JWT) is not yet decided — confirm before implementing auth flows.

## Gotchas

- No `.env` files exist yet; environment config is not implemented. Never read or print `.env` contents.
- Backend and frontend are still mostly scaffold (skeleton `Application.java`, demo `App.tsx`) — expect to build domain code from scratch.

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
- **Domain aggregates use UUID v7 surrogate PKs** via Hibernate `@UuidGenerator(style = Style.VERSION_7)` (RFC 9562 `UuidVersion7Strategy` — time-ordered, index-friendly). Use `VERSION_7`, **not** `Style.TIME` (that is RFC 4122 **v1**). First applied by the F-02 AI-memory aggregate (`ai/memory/AiMemory`).
- **Every domain table has `created_at` / `updated_at` `timestamptz` audit columns** — populated by Hibernate `@CreationTimestamp` / `@UpdateTimestamp` on `OffsetDateTime` fields (maps cleanly to `timestamptz`).
- **`jsonb` columns** map via `@JdbcTypeCode(SqlTypes.JSON)`; storing a raw JSON `String` (vs a POJO) keeps the mapping free of any Jackson coupling (used by `ai_memory_episode.payload`).
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


## Learning notes

When I ask you a question about your code, or when you completed task and generated a code and there is a new concept/framework/library/code that I've never seen before, follow these rules:

1. Intuition First: When explaining concepts, make sure they're understandable to someone who's just learning.
2. Concrete and Practical: Support any complex, abstract concepts (formulas, architecture) with a simple, concrete example or scenario.
3. "Why": Don't just explain how it works; explain why we chose this approach, the trade-offs involved, and potential errors/pitfalls.
4. Broader Perspective: Compare the concepts discussed with other technologies, languages (especially in Java as I'm Java developer), and frameworks that approach similar problems differently, so I can explore alternative approaches to architecture and patterns.
5. Active Learning Principle: Never end an answer with just a period. ALWAYS end with a specific question, a "what if" scenario, or a small problem to solve to test my understanding. Don't continue until I get the answer right — if I get it wrong, explain why and ask again in a different way.

When you complete generates code and there is a new concept/framework/library/code that I've never seen before, follow these rules:

Goal: Building intuition and active understanding, not just passive knowledge
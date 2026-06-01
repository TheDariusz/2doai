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

`/check` runs the full gate (backend tests + frontend lint + build) in one shot.

## Conventions

- **TDD — write the failing test first**, then implement. Backend uses JUnit 5; frontend has no test runner yet (add Vitest + React Testing Library when writing the first frontend test).
- **Backend base package is `com.thedariusz.todoai`** (groupId `com.thedariusz`, artifactId `todoai`). Never use a digit-leading package like `com.example.2doai` — it's invalid Java.
- Persistence: **PostgreSQL via Spring Data JPA / Hibernate.**
- TypeScript runs strict (`noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`). ESLint flat config in `eslint.config.js`; no Prettier — `eslint --fix` is the formatter.
- Git: project targets GitHub. Use feature branches, conventional commit messages, and PRs to merge.

## Deployment (Pattern B — unified origin)

- Frontend → Cloudflare Pages (static build); Backend → Fly.io (JVM).
- Single public origin: Cloudflare reverse-proxies `/api/*` to the Fly backend, so **production is same-origin (no CORS needed)**. For local dev, proxy `/api` to the backend via Vite.
- Auth (cookie vs JWT) is not yet decided — confirm before implementing auth flows.

## Gotchas

- No `.env` files exist yet; environment config is not implemented. Never read or print `.env` contents.
- Backend and frontend are still mostly scaffold (skeleton `Application.java`, demo `App.tsx`) — expect to build domain code from scratch.

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
- **Localization (PL + EN) is planned; Polish is the only locale today.** The dividing line is
  **who the text is addressed to**, not which side of the wire it lives on:
  - **Backend code → English.** Identifiers, comments, javadoc, test names, log messages.
  - **Backend → AI → English.** Prompts, system personas, field labels and enum glosses are
    instructions to a machine — code, in effect. They must *name* the language the answer comes back
    in rather than demonstrate it by being written in it (`ProposalPrompt.OUTPUT_LANGUAGE`), so a
    second locale is one line instead of a second copy of every instruction, free to drift from the
    first. A Polish prompt is the bug, even when the answer must be Polish.
  - **Anything a user reads → localized.** Frontend component copy, and the server-generated text
    that reaches them: `category.name_pl`, and `proposal/ProposalTemplate` — the fallback proposal
    *is* the sentence on the screen, so it stays Polish and a second locale is a second
    implementation of `phrase`.

  User-facing copy is hardcoded for now, but it is *content*, never the name of a concept: comments,
  javadoc, test names and docs must refer to the enum constant or the behaviour (`NEVER`,
  "withdrawn"), never to the button label ("nigdy"). A Polish string quoted as if it were the
  identity of a thing goes stale the day a second locale lands. Describe server-generated text in
  the spec the way `Category.name` already does ("in the language the server picked — Polish
  today"), not as "Polish prose".

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

## Living documentation

`docs/index.html` is the single reviewer-facing architecture document. **It is part of a slice, not
follow-up work** — the slice is not done until the page describes what actually merged. Same for
`context/foundation/openapi.yaml`: a new endpoint or wire literal moves spec + Java + TS in one commit.

**`node --test docs/index.test.mjs` passing is not evidence the page is current.** It guards
structure and a handful of pinned phrases; it cannot see prose that quietly became false.

Walk these surfaces every time — this is the list, in order, where drift has actually landed:

1. `#overview` capability table — the status badge for anything the slice moved.
2. `#code-map` — one row per backend package; a new package needs a new row.
3. `#backend` — the package's `<h3>` prose **and its class diagram**: enum values, fields, method signatures.
4. `#backend` terms `<dl>` — any new domain noun.
5. `#flows` — a new implemented flow, and any *planned* flow the slice just contradicted.
6. `#data` — the prose about which tables exist; re-export the ER SVGs only if the schema changed.
7. `#roadmap` cards and `#glossary` rows — both describe capability status in prose.
8. The endpoint list in the `#backend` note — every path the API publishes.
9. The `Verified against …` note ending `#overview` — stamp it with the issue just finished. It is the drift ledger.

**The trap, seen for real:** S-07 updated some of these and left the rest, so the page claimed two
task layers for a day while the third was already shipping — and the docs test stayed green
throughout. A partial pass reads exactly like a complete one. Walk the whole list.

Mermaid diagrams cannot be rendered headlessly here (no local Mermaid; the page loads it from CDN),
so a diagram edit is unverified until someone opens the page. Keep new diagram lines to constructs
that already appear in the file rather than inventing syntax.

## Deployment (Pattern B — unified origin)

- Frontend → Cloudflare Pages (static build); Backend → Fly.io (JVM).
- Single public origin: Cloudflare reverse-proxies `/api/*` to the Fly backend, so **production is same-origin (no CORS needed)**. For local dev, proxy `/api` to the backend via Vite.
- **Auth session model — decided (2026-07-22):** server-side **session cookie** (`HttpOnly; Secure; SameSite=Strict`), Spring Security 6 default session management, sessions held **in-memory** on the single Fly machine — **not JWT**, and never Neon-backed sessions (a per-request session `SELECT` would defeat autosuspend). Email+password; magic-link is post-MVP. Full rationale + revisit trigger: `context/foundation/auth-session-model.md`.

## Gotchas

- No `.env` files exist yet; environment config is not implemented. Never read or print `.env` contents.
- Neither half is scaffold any more: auth/session, categories, the three-layer `goal` aggregate with full CRUD, the proposal selection engine, and the React SPA (routing, guard, auth screens, `/goals`) are all implemented. Read the neighbouring slice before assuming a seam does not exist yet.

## Linear workflow (via Linear MCP)
This repo maps to the **"2doai"** project in my Linear workspace.
The Linear MCP server is connected; use it to read and update issue state —
don't ask me to paste issue details.
 
### Start of session
- Show my open issues for this project assigned to me (Dariusz) with status
  **In Progress** or **In Review** — this is my "Resume Work" filter.
  Summarize where I left off and the concrete next step for each.
- If nothing is in progress, take the highest-priority issue with status
  **Ready** (my "Next" filter) and propose starting it.
### While working
- When you start an issue, move it to **In Progress**.
- Each Linear issue carries a `branchName` — check out that branch before you
  start (I use git worktrees).
- Apply labels consistently: `backend`, `frontend`, `infrastructure`,
  `Bug`, `Feature`, `Improvement`, `Documentation`, `Maintenance`, `Research`.
### End of session
- Add a short comment to the issue: what's done, the next concrete step,
  anything blocked. This is my handoff for tomorrow.
- Before moving to **In Review**: `/check` green, and `docs/index.html` walked per
  **Living documentation** above. Both are part of the slice, not a follow-up issue.
- Move the issue to **In Review** when it's ready for review.

## Learning notes

When I ask you a question about your code, or when you completed task and generated a code and there is a new concept/framework/library/code that I've never seen before, follow these rules:

1. Intuition First: When explaining concepts, make sure they're understandable to someone who's just learning.
2. Concrete and Practical: Support any complex, abstract concepts (formulas, architecture) with a simple, concrete example or scenario.
3. "Why": Don't just explain how it works; explain why we chose this approach, the trade-offs involved, and potential errors/pitfalls.
4. Broader Perspective: Compare the concepts discussed with other technologies, languages (especially in Java as I'm Java developer), and frameworks that approach similar problems differently, so I can explore alternative approaches to architecture and patterns.
5. Active Learning Principle: Never end an answer with just a period. ALWAYS end with a specific question, a "what if" scenario, or a small problem to solve to test my understanding. Don't continue until I get the answer right — if I get it wrong, explain why and ask again in a different way.

When you complete generates code and there is a new concept/framework/library/code that I've never seen before, follow these rules:

Goal: Building intuition and active understanding, not just passive knowledge
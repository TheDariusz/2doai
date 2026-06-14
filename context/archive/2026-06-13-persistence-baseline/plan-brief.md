# Persistence Baseline (F-01) — Plan Brief

> Full plan: `context/changes/persistence-baseline/plan.md`

## What & Why

Wire a durable data layer (PostgreSQL + Spring Data JPA/Hibernate + **Flyway**)
onto the existing Spring Boot 4 skeleton and seed the **11 fixed life-domain
categories** (FR-007) as reference data. This is roadmap slice **F-01** — the root
every other slice stands on: without it, nothing a user enters is durable (the PRD's
hard durability guardrail). No user-visible effect; it's pure foundation.

## Starting Point

The backend is a bare Spring Boot 4.0.6 / Java 25 app — only `webmvc` + `actuator` +
a `PingController`. **No data layer of any kind**: no JPA, no Postgres driver, no
Flyway, no datasource config. Deploy/infra already exists (Dockerfile, `fly.toml`
always-on, Cloudflare Pattern B). The DB host is already decided in
`infrastructure.md`: external **managed Neon Postgres (EU)**, never Fly's unmanaged
Postgres.

## Desired End State

A fresh DB brought up by Flyway has a `category` table with the 11 domains in
canonical order, keyed by stable codes that mirror a `LifeDomain` Java enum. The app
boots on a real Postgres locally (Docker Compose), in tests (Testcontainers), and in
production (Neon, with Flyway-applied migrations). The project has a documented,
enforced UUIDv7 + audit-column convention and a canonical schema diagram. Fly's
liveness probe is decoupled from DB health, so a sleeping Neon never recycles the
scheduler machine.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Category model | Reference table **+** mirrored Java enum + startup drift guard | Satisfies both the DB (FK/reference data) and the AI `json_schema` enum, with a fail-fast sync check | Plan |
| ID & audit convention | UUID v7 PK + `created_at`/`updated_at` (timestamptz), documented now | Non-enumerable IDs for a multi-user API; Hibernate 7 ships an RFC 9562 v7 generator; applied from S-01 on | Plan |
| Local dev DB | Docker Compose auto-started by `spring-boot-docker-compose` | Zero-friction, reproducible, prod-identical dialect; skips itself in tests | Plan |
| Test DB | Testcontainers Postgres (`@ServiceConnection`) | Real dialect + real Flyway; also fixes the soon-to-break `@SpringBootTest` | Plan |
| Seed mechanism | Versioned `V2__seed_categories.sql` | List is fixed in MVP → an immutable, deterministic migration fits | Plan |
| Neon provisioning | Provision now + wire Fly secrets + deploy migration | Proves the durability guardrail early on free tier; matches runbook trigger | Plan |
| Health-check coupling | Split liveness/readiness; Fly probes **liveness** | A sleeping Neon must not recycle the always-on FR-011 scheduler machine | Plan |
| Scope | Strictly minimal (no domain entities) | Matches roadmap "bez encji domenowych"; entities arrive with their slice | Plan |

## Scope

**In scope:** JPA/Postgres/Flyway deps; env-driven (Neon-ready) datasource +
liveness/readiness split; local `compose.yaml`; Testcontainers test wiring;
`category` table + 11-row seed; `LifeDomain` enum; read-only `Category` entity +
repository; startup drift guard; documented persistence convention (CLAUDE.md);
canonical schema diagram (`data-model.md`); Neon provisioning + Fly secrets + deploy.

**Out of scope:** any domain entity (User/Goal/Dream/Task/AI-memory); auth & Spring
Security; base-entity abstraction; user-editable categories; vector DB/pgvector; a
categories API endpoint; frontend/CORS changes.

## Architecture / Approach

Three phases in safe order. **Phase 1** stands up the plumbing + designs the schema
(ERD first) + fixes test fidelity so the app boots on a real DB everywhere.
**Phase 2** implements the category reference data, the mirrored enum, the read-only
entity, the fail-fast drift guard, and the documented convention. **Phase 3**
provisions Neon, wires Fly secrets, moves the health probe to liveness, and deploys
so Flyway runs against prod. All migrations are expand-only (create + insert) →
backward-compatible and safe under an image rollback.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Plumbing, schema design & test fidelity | App boots on real Postgres (local + tests); ERD doc; health split | The existing `@SpringBootTest` breaks until Testcontainers is wired |
| 2. Category data + conventions | 11 seeded categories, queryable + FK-ready; enum + drift guard; convention doc | Table/enum drift if not guarded (mitigated by fail-fast check) |
| 3. Provision Neon + wire Fly + deploy | Durable cloud data layer; guardrail proven end-to-end | Human-in-the-loop cloud ops; secret hygiene; prod migration |

**Prerequisites:** Docker running locally (dev + tests); a Neon account and `flyctl`
access (Phase 3); deploy/infra already present.
**Estimated effort:** ~2–3 after-hours sessions across the three phases (Phase 3 is
short but gated on manual cloud actions).

## Open Risks & Assumptions

- **Neon free tier scales to zero** — mitigated by the liveness/readiness split and
  the always-on Fly settings; verify the machine survives a sleep/wake.
- **CI must have Docker** for `mvn test` (Testcontainers) — GitHub Actions ubuntu
  runners do; confirm the test job actually runs tests (the Dockerfile build uses `-DskipTests`).
- **Two-source category list** (table + enum) — accepted because the list is frozen
  in MVP and the startup guard catches any drift.
- **Java 25 / Spring Boot 4 are new** — verify the exact Flyway/Testcontainers
  artifact coordinates resolve under the SB4 parent during Phase 1.

## Success Criteria (Summary)

- `mvn test` boots the full context on a real Postgres and verifies the 11-category seed.
- Production runs on Neon with Flyway-applied migrations; `SELECT count(*) FROM category` = 11.
- A transient/sleeping Neon never recycles the always-on Fly machine (liveness stays `UP`).

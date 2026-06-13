# Persistence Baseline (F-01) Implementation Plan

## Overview

Wire a durable data layer onto the existing Spring Boot 4 skeleton — PostgreSQL
via Spring Data JPA/Hibernate, with **Flyway** migrations — and seed the **11
fixed life-domain categories** (FR-007) as reference data. Establish the
project-wide persistence conventions (UUIDv7 PKs + audit columns) that every
later slice inherits, then provision **managed Neon Postgres (EU)** and deploy so
the PRD's hard durability guardrail is proven end-to-end. No user-visible effect;
this is the root foundation the whole roadmap stands on.

## Current State Analysis

- **Backend is bare** (`backend/`): Spring Boot 4.0.6 / Java 25, only
  `spring-boot-starter-webmvc` + `actuator` + devtools (`pom.xml`). The only
  endpoint is `PingController` (`/api/v1/ping`). `Application.java` is the
  stock `@SpringBootApplication`.
- **No data layer at all**: no JDBC/JPA/Hibernate, no Postgres driver, no
  Flyway/Liquibase, no `db/migration`, no datasource config. `application.properties`
  holds only `spring.application.name` + actuator health exposure.
- **Tests**: `ApplicationTests.contextLoads()` is a `@SpringBootTest` (boots the
  full context); `PingControllerTest` is a sliced `@WebMvcTest`. **The full-context
  test will fail the moment a datasource bean is required**, unless tests are given
  a real (containerised) DB.
- **DB host is already decided** (`context/foundation/infrastructure.md`): external
  **managed Neon Postgres (EU)** — *never* Fly's unmanaged Postgres (durability
  guardrail; risk register). Wired via `SPRING_DATASOURCE_*` Fly secrets. The
  deployment runbook (`deployment-runbook.md:235`) explicitly defers Neon
  provisioning to "when the first slice needs it" — this is that moment.
- **Deploy/infra is present**: `backend/Dockerfile` (multi-stage, `eclipse-temurin:25`),
  `backend/fly.toml` (app `2doai`, AMS, always-on: `auto_stop_machines="off"`,
  `min_machines_running=1`, pinned 512 MB). Fly health-checks `GET /actuator/health`
  every 15s and **will recycle the machine on sustained failure**.
- **Categories are dual-referenced**: the AI auto-tag layer (`ai-provider.md:23,48`)
  classifies into the 11 domains via an LLM `json_schema` **`enum`**, so the
  domain/AI layer wants a stable code list; the roadmap (F-01) wants them as seeded
  **reference data** (a table). MVP uses **no vector DB / pgvector** — no Postgres
  extensions needed.

### Key Discoveries

- Spring Boot 4 + Hibernate ORM 7 ship `org.hibernate.id.uuid.UuidVersion7Strategy`
  — a thread-safe, monotonic **RFC 9562 UUID v7** generator reachable via
  `@UuidGenerator`. The UUIDv7 convention is real/current; it is **documented now,
  applied when S-01 builds the first domain entity** (no domain entity in this change).
- Spring Boot 4 `spring-boot-docker-compose` auto-manages a local `compose.yaml`
  service for dev and **skips itself in tests by default** (`spring.docker.compose.skip.in-tests=true`),
  so it won't collide with Testcontainers.
- Testcontainers `@ServiceConnection`
  (`org.springframework.boot.testcontainers.service.connection.ServiceConnection`)
  auto-wires the container's JDBC coordinates into the context — no manual
  `@DynamicPropertySource`. The Testcontainers BOM is managed by the Spring Boot parent.
- Fly health/Neon coupling: once a datasource exists, the `db` health indicator
  folds into the `/actuator/health` aggregate Fly probes. Neon free tier can
  scale-to-zero/sleep, so the **liveness probe must exclude DB** or a DB blip
  recycles the always-on machine and silently stops the FR-011 scheduler.
- The category natural key (stable `code`) lines up exactly with the AI enum,
  making reference rows + a mirrored Java enum the natural model.

## Desired End State

A fresh database brought up by Flyway has a `category` table populated with the
11 fixed domains, in the canonical FR-007 order, keyed by stable codes that match
a `LifeDomain` Java enum. The Spring Boot app boots against that DB locally (Docker
Compose), in tests (Testcontainers), and in production (Neon). `mvn test` boots the
full context against a real Postgres and verifies the seed. The project has a
documented, enforced persistence convention (UUIDv7 + audit columns) and a canonical
schema diagram (`context/foundation/data-model.md`). Production runs on Neon with
Flyway-applied migrations, and Fly's liveness probe is decoupled from DB health so a
sleeping Neon never recycles the scheduler machine.

**Verification**: `mvn test` green (Testcontainers); `mvn spring-boot:run` locally
auto-starts Postgres, migrates, and seeds; `fly logs` shows Flyway applying 2
migrations against Neon; `SELECT count(*) FROM category` = 11 in Neon;
`/actuator/health/readiness` UP in prod; `/actuator/health/liveness` stays UP when
the DB is briefly unreachable.

## What We're NOT Doing

- **No domain entities**: no `User`, `Goal`, `Dream`, `CurrentTask`, or AI-memory
  (`profile` / `episodic log`) tables. Those belong to S-01 / S-02 / S-07 / F-02.
  (A minimal **read-only** `Category` entity is in scope — it is reference-data
  infrastructure, not a domain aggregate.)
- **No auth, no Spring Security, no per-user data isolation** — that is S-01.
- **No JPA `@MappedSuperclass` / base-entity abstraction** — convention is documented
  in CLAUDE.md and applied when the first real entity needs it (avoid abstraction
  before a second use).
- **No user-editable categories / taxonomy** — the list is fixed in MVP (post-MVP roadmap).
- **No vector DB, pgvector, or embeddings** — explicitly out of MVP (`ai-provider.md`).
- **No API endpoint exposing categories** — added by the slice that needs it (S-02).
- **No CORS / frontend changes** — same-origin Pattern B; frontend untouched.

## Implementation Approach

Three phases, safe order: (1) stand up the data-layer plumbing and fix test
fidelity so the app boots on a real DB everywhere; (2) design-then-implement the
category reference data, the mirrored enum, the drift guard, and the documented
conventions; (3) provision the cloud DB and deploy (human-in-the-loop), proving the
durability guardrail. Schema design (the ERD) happens in Phase 1 *before* the
migration is written in Phase 2 — design-first. Migrations are pure create+insert
(inherently backward-compatible / expand-only), satisfying the runbook's
"migrations must survive an image rollback" rule.

## Critical Implementation Details

- **Liveness/readiness split is load-bearing, not cosmetic.** Fly's health check
  currently hits the `/actuator/health` aggregate, which includes `db` once a
  datasource exists. Neon free-tier sleep would then flap the always-on machine and
  silently kill the FR-011 scheduler — the exact failure in the infra risk register.
  DB health must live in **readiness only**; Fly's probe must move to **liveness**.
- **Local dev needs no `SPRING_DATASOURCE_*`.** `spring-boot-docker-compose`
  auto-derives the datasource from `compose.yaml`. Production reads `SPRING_DATASOURCE_*`
  from Fly secrets. So `application.properties` must **not** hardcode a JDBC URL.
- **`ddl-auto=validate`** (never `update`/`create`): Flyway owns the schema; Hibernate
  only validates the `Category` mapping against it — a free drift guard.
- **Neon connection discipline**: prod JDBC URL uses Neon's **pooled** endpoint with
  `sslmode=require`; Hikari `maximum-pool-size` kept small (≈5) for the 512 MB machine
  and Neon's connection ceiling. These live in the secret value + properties, never committed.
- **Two-source category sync**: the `category` table and the `LifeDomain` enum both
  list the 11 codes. A fail-fast startup guard asserts they match so drift is caught
  at boot, not at the first AI call.

## Phase 1: Data-layer plumbing, schema design & test fidelity

### Overview

Add the data-layer dependencies, configure an env-driven (Neon-ready) datasource
with the liveness/readiness split, provide a local Docker Compose Postgres, make the
full-context test boot against Testcontainers, and produce the canonical schema
diagram that Phase 2's migration will implement.

### Changes Required

#### 1. Maven dependencies

**File**: `backend/pom.xml`

**Intent**: Bring in JPA/Hibernate, the Postgres driver, Flyway (with its Postgres
module), local dev Docker Compose support, and the Testcontainers test path. No
explicit versions — the Spring Boot 4 parent manages all of these, including the
Testcontainers BOM.

**Contract**: add — `spring-boot-starter-data-jpa`; `org.postgresql:postgresql`
(runtime); `org.flywaydb:flyway-core`; `org.flywaydb:flyway-database-postgresql`;
`org.springframework.boot:spring-boot-docker-compose` (`<optional>true</optional>`,
dev-only); and test-scope `org.springframework.boot:spring-boot-testcontainers`,
`org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`.

#### 2. Local dev Postgres via Docker Compose

**File**: `backend/compose.yaml` (new)

**Intent**: Define a single `postgres` service so `mvn spring-boot:run` auto-starts a
local DB with zero manual setup; Spring derives the dev datasource from it.

**Contract**: one `postgres:17` service with `POSTGRES_DB`/`POSTGRES_USER`/
`POSTGRES_PASSWORD`, the `5432` port mapping, and a `pg_isready` healthcheck so Spring
waits for readiness before connecting.

#### 3. Datasource, JPA, Flyway & health configuration

**File**: `backend/src/main/resources/application.properties`

**Intent**: Configure JPA to validate-only (Flyway owns schema), keep Flyway enabled,
size the Hikari pool for Neon + 512 MB, and split health so the DB indicator sits in
**readiness** while Fly probes **liveness**. Do not hardcode the JDBC URL (env/compose-driven).

**Contract**: set `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`,
`spring.flyway.enabled=true`, `spring.datasource.hikari.maximum-pool-size=5`; configure
health groups so `liveness` = `livenessState` and `readiness` = `readinessState,db`
(probes already enabled via existing `management.endpoint.health.probes.enabled=true`;
keep `management.endpoints.web.exposure.include=health`).

#### 4. Testcontainers config for the full-context test

**File**: `backend/src/test/java/com/thedariusz/todoai/TestcontainersConfiguration.java` (new),
and edit `backend/src/test/java/com/thedariusz/todoai/ApplicationTests.java`

**Intent**: Give `@SpringBootTest` a real Postgres so `contextLoads` (and future
integration tests) boot with Flyway applied. `PingControllerTest` (`@WebMvcTest`) is
unaffected — it loads no datasource.

**Contract**: a `@TestConfiguration(proxyBeanMethods = false)` exposing a
`@ServiceConnection`-annotated `PostgreSQLContainer<>("postgres:17")` bean;
`ApplicationTests` adds `@Import(TestcontainersConfiguration.class)`. Container bean:

```java
@Bean
@ServiceConnection
PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:17");
}
```

#### 5. Canonical schema diagram (design-first)

**File**: `context/foundation/data-model.md` (new)

**Intent**: Establish the project's living data-model doc — a Mermaid ER diagram
designed *before* the migration. For this change it shows the `category` reference
table concretely and records the PK/audit convention as the template future
entities (User, Goal, Dream, …) follow. Future slices extend this file.

**Contract**: a `mermaid` `erDiagram` with the `category` entity (`code` PK,
`name_pl`, `display_order`) plus a short "Conventions" section stating: domain
aggregates use UUIDv7 PKs + `created_at`/`updated_at` `timestamptz`; reference tables
may use stable natural keys; snake_case columns. Planned-but-out-of-scope tables are
named in prose only (not drawn), to avoid pre-deciding their schema.

### Success Criteria

#### Automated Verification

- Build succeeds: `cd backend && mvn package`
- Full test suite passes (full context boots on Testcontainers Postgres, Flyway runs): `cd backend && mvn test`
- `PingControllerTest` still passes unchanged (no datasource pulled into the slice)
- Schema diagram exists: `context/foundation/data-model.md` present with a `category` `erDiagram`

#### Manual Verification

- `cd backend && mvn spring-boot:run` auto-starts the Compose Postgres, the app boots, `GET /actuator/health/liveness` and `GET /actuator/health/readiness` both return `UP`
- Stop the Postgres container while the app runs: `/actuator/health/liveness` stays `UP`, `/actuator/health/readiness` flips to `DOWN` (confirms the decoupling that protects the Fly machine)
- The schema diagram renders and matches the intended `category` model + convention

**Implementation Note**: After completing this phase and all automated verification
passes, pause for manual confirmation (especially the liveness/readiness decoupling
check) before proceeding to Phase 2.

---

## Phase 2: Category reference data + project conventions

### Overview

Implement the schema designed in Phase 1: the `category` table + 11-row seed via
Flyway, a mirrored `LifeDomain` enum, a minimal read-only `Category` entity/repository,
a fail-fast drift guard, and the documented persistence convention. Verified by an
integration test asserting the seed.

### Changes Required

#### 1. Schema migration

**File**: `backend/src/main/resources/db/migration/V1__create_category.sql` (new)

**Intent**: Create the `category` reference table keyed by a stable natural code.

**Contract**: table `category` with `code VARCHAR PRIMARY KEY`, `name_pl VARCHAR NOT NULL`,
`display_order INT NOT NULL UNIQUE`. No audit columns (static reference data). Pure
`CREATE` — backward-compatible.

#### 2. Category seed

**File**: `backend/src/main/resources/db/migration/V2__seed_categories.sql` (new)

**Intent**: Seed the 11 FR-007 domains, in canonical order, with stable UPPER_SNAKE
codes that the `LifeDomain` enum mirrors. Immutable versioned migration (list is
fixed in MVP). Pure `INSERT` — backward-compatible.

**Contract**: 11 rows — `display_order` 1–11 mapping code → `name_pl`:
`HEALTH`→Zdrowie, `FINANCE`→Finanse, `CAREER`→Kariera i rozwój zawodowy,
`EDUCATION`→Edukacja i rozwój osobisty, `RELATIONSHIPS`→Relacje, `HOME`→Dom i otoczenie,
`LEISURE`→Czas wolny i hobby, `ADMIN`→Sprawy formalne i administracyjne,
`SAFETY`→Bezpieczeństwo i przygotowanie na sytuacje awaryjne, `TRANSPORT`→Transport i mobilność,
`INNER_GROWTH`→Rozwój wewnętrzny / wartości.

#### 3. `LifeDomain` enum (AI/domain-facing code list)

**File**: `backend/src/main/java/com/thedariusz/todoai/category/LifeDomain.java` (new)

**Intent**: The stable, type-safe list of the 11 domain codes — the in-code mirror of
the seed and the source for the future AI `json_schema` enum (FR-008/S-09).

**Contract**: enum with the 11 constants above (names == `category.code` values). New
`category` package under the base package `com.thedariusz.todoai`.

#### 4. Read-only `Category` entity + repository

**File**: `backend/src/main/java/com/thedariusz/todoai/category/Category.java` (new),
`backend/src/main/java/com/thedariusz/todoai/category/CategoryRepository.java` (new)

**Intent**: A minimal read-only JPA mapping so reference data is queryable via Spring
Data (reused by S-02/S-08/S-09) and `ddl-auto=validate` validates the mapping against V1.

**Contract**: `@Entity` `Category` with `@Id String code`, `String namePl`, `int displayOrder`
(mapped to `name_pl` / `display_order`). `CategoryRepository extends JpaRepository<Category, String>`.
No write paths exposed.

#### 5. Fail-fast category drift guard

**File**: `backend/src/main/java/com/thedariusz/todoai/category/CategorySyncCheck.java` (new)

**Intent**: At startup, assert the DB category codes exactly match `LifeDomain`, so a
seed/enum mismatch fails the boot instead of surfacing as a bad AI classification later.

**Contract**: an `ApplicationRunner` (or `ApplicationReadyEvent` listener) comparing the
set of `CategoryRepository` codes to `LifeDomain` names; throw `IllegalStateException`
on any difference (missing, extra, or renamed).

#### 6. Documented persistence convention

**File**: `CLAUDE.md` (project root) — add to the `## Conventions` section

**Intent**: Record the project-wide rules every future slice inherits, so they aren't
re-derived per slice.

**Contract**: a short "Persistence" subsection stating — domain aggregates use **UUID v7**
surrogate PKs via Hibernate `@UuidGenerator` (RFC 9562, `UuidVersion7Strategy`); every
domain table has `created_at`/`updated_at` `timestamptz` audit columns; columns are
snake_case; reference tables may use a stable natural key (e.g. `category.code`); schema
is owned by **Flyway** (`db/migration`, versioned, backward-compatible expand/contract);
Hibernate runs `ddl-auto=validate`.

#### 7. Category seed integration test

**File**: `backend/src/test/java/com/thedariusz/todoai/category/CategorySeedTest.java` (new)

**Intent**: Prove the migration seeds exactly the 11 expected domains and that the table
and enum agree.

**Contract**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` asserting
`CategoryRepository.count() == 11`, the set of codes equals `LifeDomain` names,
`display_order` values are 1–11 and unique, and every `name_pl` is non-blank.

### Success Criteria

#### Automated Verification

- Migration applies + seed verified on a real Postgres: `cd backend && mvn test` (includes `CategorySeedTest`)
- `ddl-auto=validate` passes — the `Category` mapping matches `V1` (context boot would fail otherwise)
- Full build passes: `cd backend && mvn package`

#### Manual Verification

- Fresh DB: `mvn spring-boot:run` → `fly`/app log shows Flyway applying V1 + V2; `SELECT code, name_pl, display_order FROM category ORDER BY display_order` lists the 11 domains in order
- Drift guard works: temporarily remove a seed row (or an enum constant) → app boot fails fast with a clear `IllegalStateException` (revert after)
- CLAUDE.md "Persistence" convention reads clearly and matches the diagram

**Implementation Note**: After this phase and all automated verification passes, pause
for manual confirmation before proceeding to Phase 3 (which performs irreversible-ish
cloud provisioning and a production deploy).

---

## Phase 3: Provision Neon + wire Fly + deploy (human-in-the-loop)

### Overview

Stand up the managed cloud DB, wire production secrets, move Fly's health probe to
liveness, and deploy so Flyway runs against Neon — proving the durability guardrail.
Several steps require the human (cloud provisioning, secret rotation, prod deploy).

### Changes Required

#### 1. Provision Neon (EU) — human action

**Intent**: Create the managed Postgres that holds all production data, with automated
backups/PITR (durability guardrail). External managed Postgres only — never Fly's
unmanaged Postgres.

**Contract**: a Neon project in an EU region; capture the **pooled** connection string
with `sslmode=require`. Run interactively (`! ` prefix) or via the Neon console. The DB
URL/credentials are **never** printed, committed, or logged.

#### 2. Wire Fly secrets — human action

**Intent**: Inject the datasource into the running backend without committing anything.

**Contract**: `fly secrets set SPRING_DATASOURCE_URL=… SPRING_DATASOURCE_USERNAME=… SPRING_DATASOURCE_PASSWORD=…`
(values from step 1). `fly secrets list` shows digests only. Promoting/rotating the
primary DB credential is a human-approved op per the runbook.

#### 3. Move Fly health probe to liveness

**File**: `backend/fly.toml`

**Intent**: Ensure a sleeping/transient Neon never recycles the always-on machine that
runs the FR-011 scheduler.

**Contract**: change the `[[http_service.checks]]` `path` from `/actuator/health` to
`/actuator/health/liveness`. Leave the always-on settings (`auto_stop_machines="off"`,
`min_machines_running=1`, pinned memory) untouched.

#### 4. Record rollback point — human/agent ops

**Intent**: There is no one-shot Fly rollback; capture the current release image before
deploying so recovery is `fly deploy --image <prev-tag>`.

**Contract**: `fly releases` / note the current image digest before step 5. Migrations
are expand-only, so an image rollback leaves the new table harmlessly unused.

#### 5. Deploy & migrate — human-approved

**Intent**: Ship the data layer; Flyway applies V1 + V2 against Neon on first boot.

**Contract**: `fly deploy` (Dockerfile builder). On boot Flyway migrates the empty Neon
DB. `fly status` healthy; `fly logs` shows the Flyway summary.

### Success Criteria

#### Automated Verification

- Backend GitHub Actions deploy workflow completes green (build + deploy)
- `fly status` reports a healthy, running machine after deploy

#### Manual Verification

- `fly logs -a 2doai` shows Flyway "Successfully applied 2 migrations" against Neon
- Prod `GET /actuator/health/readiness` returns `UP` (Neon reachable); `/actuator/health/liveness` `UP`
- Neon SQL console: `SELECT count(*) FROM category` returns `11`, ordered list correct
- Prior release image digest was recorded before deploy (rollback path documented)
- Fly machine stays `UP` across a brief Neon sleep/wake (liveness decoupling holds) — observational
- `fly secrets list` shows the three datasource secrets as digests (no plaintext anywhere)

**Implementation Note**: This phase changes production. Confirm each human action
(provision, secrets, deploy) explicitly; never echo secret values.

---

## Testing Strategy

### Unit / slice tests

- `PingControllerTest` (`@WebMvcTest`) remains the fast slice — must stay green and pull no datasource.

### Integration tests (Testcontainers Postgres, real Flyway)

- `ApplicationTests.contextLoads()` — full context boots on a real Postgres with migrations applied.
- `CategorySeedTest` — 11 categories seeded; codes match `LifeDomain`; `display_order` 1–11 unique; `name_pl` non-blank.
- Drift guard exercised implicitly (context boot fails if DB ≠ enum).

### Manual testing steps

1. `mvn spring-boot:run` locally → Compose Postgres auto-starts, Flyway migrates+seeds, both health groups `UP`.
2. Kill the DB container → liveness `UP`, readiness `DOWN` (Fly-safety check).
3. Inspect seeded rows via psql/Neon console (ordered 11 domains).
4. Post-deploy: `fly logs` Flyway summary + Neon `SELECT count(*)` = 11.

## Performance Considerations

- Hikari `maximum-pool-size=5` keeps connections within Neon's free-tier ceiling and the
  512 MB machine's budget; revisit only at real load (out of MVP scope).
- Neon pooled endpoint avoids per-connection cold starts. JVM heap already capped via
  `-XX:MaxRAMPercentage=70` in the Dockerfile.

## Migration Notes

- All migrations are **expand-only** (create table, insert rows) → backward-compatible and
  safe under an image rollback (the new table simply goes unused if the prior image returns).
- Schema is Flyway-owned; Hibernate never alters it (`ddl-auto=validate`).
- Future destructive/altering migrations must follow expand/contract per the runbook.

## References

- Roadmap slice: `context/foundation/roadmap.md` → F-01 (`persistence-baseline`)
- Durability + DB-host decision: `context/foundation/infrastructure.md` (risk register; Neon, not Fly Postgres)
- Deploy/secrets/rollback runbook: `context/foundation/deployment-runbook.md` (line 235 trigger)
- Categories + AI enum coupling: `context/foundation/prd.md` (FR-007) and `context/foundation/ai-provider.md:23,48`
- Existing config to extend: `backend/src/main/resources/application.properties`, `backend/fly.toml`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Data-layer plumbing, schema design & test fidelity

#### Automated

- [x] 1.1 Build succeeds: `cd backend && mvn package`
- [x] 1.2 Full test suite passes on Testcontainers Postgres: `cd backend && mvn test`
- [x] 1.3 `PingControllerTest` still passes unchanged (no datasource in the slice)
- [x] 1.4 Schema diagram exists: `context/foundation/data-model.md` with a `category` `erDiagram`

#### Manual

- [x] 1.5 `mvn spring-boot:run` auto-starts Compose Postgres; liveness + readiness both `UP`
- [x] 1.6 DB container stopped → liveness stays `UP`, readiness `DOWN` (decoupling confirmed)
- [x] 1.7 Schema diagram renders and matches intended `category` model + convention

### Phase 2: Category reference data + project conventions

#### Automated

- [ ] 2.1 Migration applies + seed verified: `cd backend && mvn test` (incl. `CategorySeedTest`)
- [ ] 2.2 `ddl-auto=validate` passes — `Category` mapping matches `V1`
- [ ] 2.3 Full build passes: `cd backend && mvn package`

#### Manual

- [ ] 2.4 Fresh DB: Flyway applies V1+V2; 11 domains present in canonical order
- [ ] 2.5 Drift guard fails boot fast when DB ≠ enum (then reverted)
- [ ] 2.6 CLAUDE.md "Persistence" convention reads clearly and matches the diagram

### Phase 3: Provision Neon + wire Fly + deploy

#### Automated

- [ ] 3.1 Backend GitHub Actions deploy workflow completes green
- [ ] 3.2 `fly status` reports a healthy, running machine after deploy

#### Manual

- [ ] 3.3 `fly logs` shows Flyway "Successfully applied 2 migrations" against Neon
- [ ] 3.4 Prod `/actuator/health/readiness` `UP`; `/actuator/health/liveness` `UP`
- [ ] 3.5 Neon `SELECT count(*) FROM category` = 11, ordered list correct
- [ ] 3.6 Prior release image digest recorded before deploy
- [ ] 3.7 Fly machine stays `UP` across a brief Neon sleep/wake (observational)
- [ ] 3.8 `fly secrets list` shows the 3 datasource secrets as digests only

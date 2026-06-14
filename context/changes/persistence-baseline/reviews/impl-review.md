<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Persistence Baseline (F-01)

- **Plan**: context/changes/persistence-baseline/plan.md
- **Scope**: Full plan — Phases 1–3 of 3
- **Date**: 2026-06-14
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Evidence highlights

- Every planned item across all three phases MATCHed. Deviations from the plan's
  literal text are corrections, not drift: dev/test images use `postgres:18`
  (not the plan's stale `17`), honoring the CLAUDE.md PG-18 pin and matching prod
  (Neon); the Boot-4-required `spring-boot-flyway` module is added; V1 uses explicit
  `VARCHAR` lengths (32/255). All benign and consistent with intent.
- Schema ↔ JPA mapping ↔ enum all agree: `Category` maps cleanly to V1 columns
  (`ddl-auto=validate` passes); the 11 V2 seed codes match `LifeDomain` in identical
  order (display_order 1–11, contiguous, unique); all Polish `name_pl` values present
  with correct diacritics.
- No scope-guardrail violations: no domain entities, no auth/Spring Security, no
  `@MappedSuperclass`, no category API endpoint, no frontend/CORS changes — only the
  in-scope read-only `Category`.
- No secret leakage: `compose.yaml` local-dev creds only; `application.properties`
  omits the JDBC URL (prod via Fly `SPRING_DATASOURCE_*` secrets); `.neon` gitignored.
- Success criteria PASS: local `mvn package` BUILD SUCCESS, 6 tests green on
  PostgreSQL 18.4 (`CategorySeedTest` ×4, `ApplicationTests`, `PingControllerTest`);
  Phase 3 CI deploy green (`run 27501757395`), Fly machine healthy, Neon seeded with
  11 categories, liveness/readiness split verified in prod.

## Findings

### F1 — Drift guard fails boot after the web server starts

- **Severity**: 🟢 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality (Reliability)
- **Location**: backend/src/main/java/com/thedariusz/todoai/category/CategorySyncCheck.java:18-40
- **Detail**: `CategorySyncCheck` is an `ApplicationRunner`; runners fire after context
  refresh and after the embedded web server is already accepting traffic. On a
  seed/enum mismatch it throws `IllegalStateException`, which propagates out of
  `SpringApplication.run` and exits the JVM non-zero — so boot does fail — but there is
  a sub-second window where the app serves `/api` before exiting. The plan explicitly
  sanctioned "ApplicationRunner (or ApplicationReadyEvent listener)", so this is
  plan-conformant, not drift. Hardening option only.
- **Fix**: Optional — move the check to an `InitializingBean` / `@PostConstruct` (or an
  `@EventListener` on a context-refresh event) so it runs during refresh, before the
  server binds, for strict pre-bind fail-fast. Acceptable as-is for MVP.
- **Decision**: PENDING

### F2 — Two Flyway modules on the classpath (confirmed correct)

- **Severity**: 🟢 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality (Reliability)
- **Location**: backend/pom.xml:61-68
- **Detail**: Both `flyway-core` and `flyway-database-postgresql` are declared. Flyway
  split DB support into per-vendor modules, so `flyway-database-postgresql` is required
  for PostgreSQL on this Flyway version. The green build (Flyway applied V1+V2; 6 tests
  pass on PG 18.4) confirms the modules resolve correctly with no conflict.
- **Fix**: None — keep both. Dropping `flyway-database-postgresql` would break PG
  migration support under Boot 4.
- **Decision**: PENDING

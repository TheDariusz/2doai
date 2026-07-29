# Account & Auth (S-01) Implementation Plan

## Overview

Establish the first per-user boundary in 2do AI: **Spring Security 6** on the
already-decided session model, a `User` aggregate, a **per-user data-isolation
contract** every later slice inherits, **FR-019** account+data deletion, and the
**React app shell** (built from today's demo scaffold). After this slice a person
can register (email+password), log in / out, hit gated routes (redirected to login
when unauthenticated), and permanently delete their account and all data.

The single biggest decision — cookie vs JWT — is **already settled** in
`context/foundation/auth-session-model.md`: a server-side session cookie
(`HttpOnly; Secure; SameSite=Strict`), Spring Security 6 default session
management, sessions **in-memory** on the single always-on Fly machine, CSRF via
`SameSite=Strict` + Spring's built-in token. Not JWT, never Neon-backed sessions.

## API Design Amendment (2026-07-22 — Zalando review)

After a `/zalando-api` review, the REST surface was redesigned to the Zalando RESTful API
Guidelines. **`context/changes/account-and-auth/openapi.yaml` is now the authoritative API
contract** (API-first, #100/#101); where any older phase prose below still names an auth-verb
path (`/auth/register`, `/auth/login`, …), this amendment + the OpenAPI spec govern.

**Endpoints — resource-oriented, verb-free (#141/#138), no URL version (#115/#113):**

| Purpose | Path & method |
| --- | --- |
| Register | `POST /api/users` → 201 (`Location: /api/users/me`) |
| Log in | `POST /api/sessions` → 201 (`Set-Cookie`; body = current user) |
| Log out | `DELETE /api/sessions/current` → 204 |
| Current user (SPA bootstrap) | `GET /api/users/me` → 200 / 401 |
| Delete account (FR-019) | `DELETE /api/users/me` → 204 (body = `{password}` re-auth) |
| Categories | `GET /api/categories` → 200 `{ items: [...] }` |

**Payload / error conventions applied to all Phase 2+ work:**

- **snake_case** JSON properties (#118) via a global Jackson strategy (`spring.jackson.property-naming-strategy=SNAKE_CASE`) — so `category` serializes `name_pl` / `display_order`, not camelCase.
- **Collections wrap in a top-level object** (#110): categories returns `{ items: [...] }`, never a bare array. (Not paginated — 11 fixed rows, a documented #159/#226 exception.)
- **Errors are Problem JSON / RFC 9457** (#176) via Spring's `ProblemDetail` in a `@RestControllerAdvice`; **validation → 422** (#220), duplicate email → 409, bad credentials → generic 401.
- **`x-audience: component-internal`, `x-api-id`** set in the spec (#215/#219).
- **CSRF priming correction** (correctness fix found during the review): the old "make the `GET /me` handler read the `CsrfToken`" approach can't prime the cookie on the *unauthenticated* first load — `/users/me` is gated, so the filter chain returns 401 before the handler runs. Replaced by a `CsrfCookieFilter` (ordered before the authorization filter) that renders the token on every request, so the `XSRF-TOKEN` cookie is set even on the bootstrap 401. **Implemented in Phase 1** and covered by `SecurityConfigTest`.

## Current State Analysis

- **Backend** (`backend/`, Spring Boot 4.0.6, Java 25): `AiMemory` aggregate
  (`ai/memory/AiMemory.java`) is the pattern to mirror — UUID v7 PK via
  `@UuidGenerator(Style.VERSION_7)`, `timestamptz` audit columns, rich model with
  intent-revealing methods and no public setters. `category` reference table +
  `LifeDomain` enum (the frozen 11 FR-007 domains). `PingController`
  (`/api/ping`) is the only controller. Flyway `V1`–`V3` exist.
  **No Spring Security dependency in `pom.xml`** — it is net-new.
- **Deferred FK**: `ai_memory.user_id` is a plain `NOT NULL UNIQUE` UUID today; its
  FK to the user table is explicitly deferred to this slice (comment in
  `V3__create_ai_memory.sql:14-17`, and `data-model.md:60-64`). `ai_memory` has **no
  rows** yet (F-02 shipped no writers), so adding the FK is a safe expand-only op.
- **Config** (`application.properties`): load-bearing Hikari drain + liveness/
  readiness split — must not be disturbed. Fly's probe hits
  `/actuator/health/liveness` **through the JVM's filter chain**, so that path must
  stay public once Security is added.
- **Frontend** (`frontend/`): demo scaffold. `App.tsx` is the Vite starter; **no
  router, no API client, no auth state**. React 19 + TS strict + Vitest + RTL.
  The Vite `/api → localhost:8080` proxy already exists (`vite.config.ts:11`) —
  local dev is same-origin, mirroring Pattern B.
- **Deployment**: Pattern B same-origin (no CORS in prod). Neon idleness cost rule
  (`lessons.md`) — in-memory sessions honor it (session validation never queries
  the DB); auth endpoints query the DB but are user-initiated and rare.

### Key Discoveries

- Mirror the aggregate conventions in `ai/memory/AiMemory.java:38-76` (UUID v7,
  audit columns, invariant-in-constructor).
- The AI-memory aggregate deletes whole via JPA cascade: `AiMemory` has
  `CascadeType.ALL` + `orphanRemoval` on both child sets
  (`AiMemory.java:54-58`), so `repository.delete(memory)` removes children then
  root — the account-deletion path reuses this, no DB `ON DELETE CASCADE` needed on
  the ai_memory children.
- `findByUserId` (`AiMemoryRepository.java:25-26`) is the shape of the per-user
  scoping the whole app inherits.
- `spring-boot-starter-validation` is already present; the project prefers **Jakarta
  Bean Validation** (memory: `prefer-apache-commons-helpers`) for input constraints.

## Desired End State

Running `/check` is green. Against a live backend + `npm run dev`:

1. A new user registers with email+password, is told if the email is taken, then
   logs in and lands on the app shell (header with their email + logout, a
   navigation listing the 11 life domains, placeholder domain content).
2. Visiting any gated route while logged out redirects to `/login`; after login the
   user returns to where they were headed.
3. Logout invalidates the session; the gated routes are inaccessible again.
4. "Delete account" requires password re-entry + an explicit confirm, then
   permanently removes the user and their `ai_memory` (profile facts + episodes);
   the session is invalidated and the email can be re-registered.
5. All mutating requests carry the CSRF token; a request without it is rejected.

## What We're NOT Doing

- **No JWT / stateless tokens**, no Redis/Neon-backed sessions — in-memory, decided.
- **No email verification, no password reset, no magic-link** — all need the email
  infra that arrives with FR-018 (post-MVP). Email is a plain unique login id.
- **No rate limiting / account lockout / breach-list** — baseline hardening only
  (BCrypt, min-8, generic auth errors, CSRF).
- **No remember-me / persistent login across restarts** — session cookie + idle
  timeout; a deploy logs everyone out (accepted at 1–10 users).
- **No OAuth / social login, no admin roles, no shared workspaces / sharing** —
  flat multi-user model.
- **No Postgres RLS, Hibernate `@Filter`, or ArchUnit enforcement** — manual
  query-scoping by authenticated userId, guarded by convention + tests. We *do* lay
  the hook these would attach to (the `UserOwned` marker + universal `user_id`
  column), so any of them is cheap to adopt later; we don't build the enforcement now.
  - **Forward-pointer:** the enforcing ArchUnit rule — *"every `@Entity` mapping a
    `user_id` column must implement `UserOwned`"* — arrives with **S-02** (the second
    per-user entity), not S-01. Install it as replication begins, before a forgetful
    copy can ship silently. Note the rule must **select on the `user_id` column and
    assert the marker** (not the reverse) — selecting on `UserOwned` would let an
    entity that *forgot* the marker escape the suspect set and pass vacuously.
- **No per-domain feature screens** (S-02+); the nav links to a placeholder page.
- **No E2E/Playwright harness** — focused security-path tests only.

## Implementation Approach

Four phases, backend-first, each independently green with a manual checkpoint.
Phases 1–2 establish the server contract; Phase 3 delivers the core S-01 user
outcome on the frontend; Phase 4 adds the fuller shell (opted-in).

**Decisions locked during planning** (see `plan-brief.md` for the table):
deletion = app-orchestrated `AccountDeletionService` with an orphan-guard;
isolation = query-scoping by authenticated userId, with a `UserOwned` marker +
scoped-access convention laid now so an ArchUnit/RLS structural guard is cheap
later; ai_memory provisioned eagerly in
the registration transaction; `User` = minimal identity; CSRF = XSRF-TOKEN cookie →
`X-XSRF-TOKEN` header echo; no email verification; baseline hardening; session
cookie + idle timeout; fuller frontend shell; React Router + Context + fetch;
focused tests.

## Critical Implementation Details

- **Reserved-word table name.** `user` is a reserved word in Postgres; naming the
  table `user` forces double-quoting in every migration and native query. Name it
  **`app_user`** (Java entity `User`, `@Table(name = "app_user")`). Read
  `data-model.md`'s and `V3`'s prose "`user(id)`" as "the user table" — update those
  references to `app_user(id)` opportunistically.
- **Spring Security 6 deferred CSRF token.** With `CookieCsrfTokenRepository`, SS6
  defers token generation until the token is *read*, so the `XSRF-TOKEN` cookie may
  never be set for a client that only calls read endpoints first — and the SPA's
  bootstrap `GET /api/users/me` is *gated*, so on the unauthenticated first load the
  filter chain returns 401 **before any handler runs**, meaning a "let the `/me` handler
  read the token" approach cannot prime the cookie. Instead a `CsrfCookieFilter` (ordered
  before the authorization filter) renders the token on every request, so the cookie is
  written even on the bootstrap 401; the config uses `CsrfTokenRequestAttributeHandler`
  (the SPA-safe handler that reads the raw token from the header, not the BREACH-masked
  form value). **Implemented in Phase 1**, covered by `SecurityConfigTest`.
- **Dev vs prod cookie `Secure` flag.** In local dev the browser talks to Vite over
  **http** (`localhost:5173`), so a `Secure` session/CSRF cookie is silently dropped
  and login appears to "not persist." Set `Secure` **only in prod** (Fly env
  `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`); leave it off in dev.
  `SameSite=Strict` and `HttpOnly` stay on everywhere.
- **Security tests use REST Assured, not MockMvc.** HTTP-layer/endpoint tests run over
  real HTTP against a random-port embedded server (`@SpringBootTest(webEnvironment =
  RANDOM_PORT)`), so the real Spring Security filter chain is in the path. The old
  `PingControllerTest` `@WebMvcTest` slice is replaced by `SecurityGatewayApiTest`
  (REST Assured). Keep `/api/ping` and `/actuator/health/**` in the `permitAll` set;
  `spring-security-test`/MockMvc are not on the classpath.

---

## Phase 1: Backend security foundation + `User` aggregate + migrations

### Overview

Add Spring Security, the `app_user` table and the deferred FK, the `User`
aggregate, and the `SecurityConfig` that wires the decided cookie-session model and
the current-user accessor — the seam the isolation contract hangs on. No auth
endpoints yet; success is "app boots with Security on, mappings validate, gated
routes 401, public routes stay 200."

### Changes Required

#### 1. Security dependencies

**File**: `backend/pom.xml`

**Intent**: Add Spring Security (filter chain, password encoding, CSRF) plus REST Assured
for HTTP-layer endpoint tests.

**Contract**: Add `spring-boot-starter-security` (compile; version from the Boot 4 parent
BOM) and `io.rest-assured:rest-assured` (test; version pinned via a `rest-assured.version`
property — **not** managed by the Boot 4 BOM, so `6.0.1` for Hamcrest 3 / Groovy 4 / Java 17+
alignment). MockMvc / `spring-security-test` are not used.

#### 2. `app_user` table

**File**: `backend/src/main/resources/db/migration/V4__create_app_user.sql`

**Intent**: Persist the user identity aggregate, following the project's UUID-v7 +
`timestamptz` audit conventions.

**Contract**: `CREATE TABLE app_user (id UUID PK, email VARCHAR(320) NOT NULL
UNIQUE, password_hash VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
updated_at TIMESTAMPTZ NOT NULL)`. `email` stores the normalized (lowercased) value;
the `UNIQUE` index also serves login lookup. Pure `CREATE` (expand-only).

#### 3. Expand-only FK on `ai_memory.user_id`

**File**: `backend/src/main/resources/db/migration/V5__ai_memory_user_fk.sql`

**Intent**: Turn the deferred `ai_memory.user_id` into a real FK now that
`app_user` exists — closing the intentional gap noted in `V3`.

**Contract**: `ALTER TABLE ai_memory ADD CONSTRAINT fk_ai_memory_user FOREIGN KEY
(user_id) REFERENCES app_user (id)`. **No `ON DELETE CASCADE`** — deletion is
app-orchestrated, and a plain FK makes an out-of-order delete of `app_user` fail
loudly (the DB backstop behind the orphan-guard). Safe because `ai_memory` is empty.

#### 4. `User` aggregate + `Email` value object

**File**: `backend/src/main/java/com/thedariusz/todoai/user/User.java`,
`user/Email.java`

**Intent**: A rich identity aggregate mirroring `AiMemory` (UUID v7 PK, audit
columns, invariant-in-constructor, no public setters). `Email` is a value object
that validates format and normalizes to lowercase, centralizing the identity
invariant (matches the project's DDD preference).

**Contract**: `User(Email email, String passwordHash)` — stores the normalized
email string and the already-hashed password (the raw password never enters the
domain). Getters only. `Email.of(String)` validates + lowercases; rejects malformed
input. Map `@Table(name = "app_user")`, `email` column `unique = true`.

#### 5. `UserRepository`

**File**: `backend/src/main/java/com/thedariusz/todoai/user/UserRepository.java`

**Intent**: Spring Data access for login lookup and existence checks.

**Contract**: `JpaRepository<User, UUID>` with `Optional<User> findByEmail(String
email)` and `boolean existsByEmail(String email)`.

#### 6. `UserDetailsService` + `UserPrincipal`

**File**: `backend/src/main/java/com/thedariusz/todoai/security/UserPrincipal.java`,
`security/AppUserDetailsService.java`

**Intent**: Bridge the domain `User` to Spring Security's authentication model. The
principal carries the user's **UUID** (the value the isolation contract keys on) and
email.

**Contract**: `UserPrincipal implements UserDetails` exposing `UUID userId()`,
username = email, password = hash, empty authorities. `AppUserDetailsService
implements UserDetailsService` loads by email via `UserRepository`, throwing
`UsernameNotFoundException` when absent (mapped to a generic 401 upstream).

#### 7. `SecurityConfig`

**File**: `backend/src/main/java/com/thedariusz/todoai/security/SecurityConfig.java`

**Intent**: Wire the decided model — cookie session, BCrypt, CSRF cookie repo, and
the authorization rules that gate everything except public infra + the auth entry
points.

**Contract**: Beans — `SecurityFilterChain`, `PasswordEncoder`
(`PasswordEncoderFactories.createDelegatingPasswordEncoder()` → bcrypt),
`AuthenticationManager`. Authorization (method-specific): `permitAll` for `POST /api/users`
(register), `POST /api/sessions` (login), `/api/ping`, `/actuator/health/**`; everything
else `authenticated()` (so `GET`/`DELETE /api/users/me` and `DELETE /api/sessions/current`
stay gated). CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` +
`CsrfTokenRequestAttributeHandler`, plus a `CsrfCookieFilter` that primes the `XSRF-TOKEN`
cookie on every request (see Critical Details). Session management left at the SS6 default
(creates on auth, fixation protection = `changeSessionId`). Unauthenticated API requests
return **401** (not a redirect — the SPA owns navigation); supply an
`AuthenticationEntryPoint` that writes 401 rather than the default login redirect.

#### 8. Current-user accessor

**File**: `backend/src/main/java/com/thedariusz/todoai/security/CurrentUser.java`

**Intent**: The single seam every per-user query uses to obtain the authenticated
userId — the enforcement point of the query-scoping isolation contract.

**Contract**: `UUID requireId()` reads `SecurityContextHolder` → `UserPrincipal` →
`userId()`; throws (→ 401) if unauthenticated. All later slices scope their queries
through this rather than trusting a client-supplied id.

#### 9. `UserOwned` marker + scoped-access convention

**File**: `backend/src/main/java/com/thedariusz/todoai/security/UserOwned.java`,
applied to `ai/memory/AiMemory.java` (+ a `package-info.java` documenting the rule)

**Intent**: Establish the per-user data-access **choke point now, while `AiMemory`
is the only per-user entity**, so adopting a stronger *structural* guard later — an
ArchUnit rule, or Postgres RLS — is additive rather than a cross-slice retrofit. The
cost of the convention scales with how many repositories exist when it's adopted;
that number is smallest today (one). This is the cheap insurance against the one
failure mode query-scoping is exposed to: a forgotten `WHERE user_id = ?`.

**Contract**: A marker interface `UserOwned { UUID getUserId(); }` implemented by
`AiMemory` (which already exposes `getUserId()` — no behavior change). Document the
convention once (package-info / the isolation test): *every per-user aggregate
implements `UserOwned`, carries a `user_id` column, and is read only through a
`CurrentUser`-scoped finder — never a client-supplied id.* The payoff is that the
isolation seam becomes **named and scannable**: the same marker is what a future
ArchUnit rule ("no `UserOwned` finder bypasses the scoped path") keys off, and the
universal `user_id` column is what RLS policies key on. ArchUnit enforcement + RLS
stay out of scope here (see *What We're NOT Doing*); this slice only lays the hook
they attach to.

#### 10. Session cookie properties

**File**: `backend/src/main/resources/application.properties`

**Intent**: Set the decided cookie attributes + idle timeout, with `Secure`
env-driven for the dev/prod http/https split.

**Contract**: `server.servlet.session.timeout=30m`,
`server.servlet.session.cookie.http-only=true`,
`server.servlet.session.cookie.same-site=strict`,
`server.servlet.session.cookie.secure=${SERVER_SERVLET_SESSION_COOKIE_SECURE:false}`.
Do not touch the Hikari / actuator blocks.

#### 11. Security-gateway REST Assured integration test

**File**: `backend/src/test/java/com/thedariusz/todoai/SecurityGatewayApiTest.java`
(replaces the old `PingControllerTest` `@WebMvcTest` slice)

**Intent**: Prove the public/gated boundary end-to-end over real HTTP — the project's
standard for endpoint tests.

**Contract**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers, driven with
REST Assured. Assert `GET /api/ping` → 200 (permitAll smoke), a gated path → 401 anonymous,
and the `XSRF-TOKEN` cookie is primed even on that 401. The authenticated → 200 case has no
login endpoint yet — it is proven by Phase 2's REST Assured lifecycle.

### Success Criteria

#### Automated Verification

- Backend compiles + mappings validate against the migrated schema (`ddl-auto=validate`): `cd backend && mvn -q test-compile`
- Migrations apply cleanly on a fresh Testcontainers DB (V1–V5): `cd backend && mvn -q test -Dtest=ApplicationTests`
- Existing suite stays green, incl. the updated ping test: `cd backend && mvn test`
- A gated request returns 401 and a `permitAll` request returns 200 (`SecurityGatewayApiTest`, REST Assured)
- Convention held: `AiMemory implements UserOwned` and its per-user reads route through the `CurrentUser`-scoped finder (unit test)

#### Manual Verification

- `mvn spring-boot:run` boots with Security enabled and no schema-drift error
- Hitting a gated `/api/*` path unauthenticated returns 401 (not an HTML redirect); `/api/ping` and `/actuator/health/liveness` return 200

**Implementation Note**: After automated verification passes, pause for manual
confirmation before Phase 2.

---

## Phase 2: Backend auth flows + FR-019 deletion + isolation + categories

### Overview

The behavior layer: register (with eager `ai_memory` provisioning), login, logout,
`me`, delete-account, the `AccountDeletionService` + orphan-guard, and the read-only
categories endpoint the nav consumes. Integration tests prove isolation, deletion
completeness, gating, and CSRF.

### Changes Required

#### 1. Auth DTOs + validation

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/` (request/response
records)

**Intent**: Typed, validated request/response bodies for the auth endpoints.

**Contract**: `RegisterRequest(email, password)` and `LoginRequest(email,
password)` with Jakarta Bean Validation (`@Email`, `@NotBlank`, `@Size(min = 8)` on
registration passwords, and a 72-UTF-8-byte BCrypt maximum on every password request).
`UserResponse(UUID id, String email)` for register/login/me (the `User` schema in
`openapi.yaml`). `DeleteAccountRequest(String password)` for the re-auth on FR-019.
**All JSON serializes snake_case** via
`spring.jackson.property-naming-strategy=SNAKE_CASE` (#118) — set once, globally.

#### 2. `RegistrationService` (eager ai_memory provisioning)

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/RegistrationService.java`

**Intent**: Create the user and their `ai_memory` root in one transaction so the
"every user has exactly one memory" invariant holds from t=0 and the new FK is
immediately satisfiable.

**Contract**: `@Transactional register(email, rawPassword)`: reject a duplicate
email (→ 409); hash via `PasswordEncoder`; persist `User`; persist a `new
AiMemory(user.getId())` via `AiMemoryRepository`. Registration does **not**
auto-login (single session-creation path = login).

#### 3. Users + sessions controllers

**File**: `backend/src/main/java/com/thedariusz/todoai/user/UserController.java` +
`session/SessionController.java` (resource-oriented; a single `AuthController` serving both
resources is acceptable).

**Intent**: The REST surface for the `users` + `sessions` resources — see `openapi.yaml`
(authoritative). Verb-free paths, snake_case bodies, Problem JSON errors.

**Contract**:
- `POST /api/users` (register) → 201 + `UserResponse` + `Location: /api/users/me` (or 409
  duplicate). Public.
- `POST /api/sessions` (login) → authenticates via `AuthenticationManager`, persists the
  `SecurityContext` into the session (`HttpSessionSecurityContextRepository`) with
  session-id rotation (fixation protection), returns **201** + `Set-Cookie` + `UserResponse`.
  Generic **401** on bad credentials (no user-enumeration). Public.
- `DELETE /api/sessions/current` (logout) → invalidates the session + clears the cookie,
  204. Authenticated.
- `GET /api/users/me` → 200 + `UserResponse` for the current user, else 401. Authenticated.
  (The `XSRF-TOKEN` cookie is primed by the `CsrfCookieFilter`, not this handler.)
- `DELETE /api/users/me` → re-verifies the submitted password against the current user,
  runs `AccountDeletionService`, invalidates the session, 204. Authenticated + CSRF.

#### 4. `PerUserDataDeleter` port + `AiMemoryDataDeleter`

**File**: `backend/src/main/java/com/thedariusz/todoai/account/PerUserDataDeleter.java`,
`ai/memory/AiMemoryDataDeleter.java`

**Intent**: A registry seam so each module owns the deletion of its own per-user
data; the ai_memory implementation lands now.

**Contract**: `interface PerUserDataDeleter { void deleteAllForUser(UUID userId);
String userScopedTable(); }`. `AiMemoryDataDeleter` loads the memory via
`findByUserId` and `repository.delete(it)` (JPA cascade removes children); reports
table `ai_memory`.

#### 5. `AccountDeletionService` + orphan guard

**File**: `backend/src/main/java/com/thedariusz/todoai/account/AccountDeletionService.java`

**Intent**: Orchestrate FR-019 — delete all per-user data, then the user, in one
transaction; make a forgotten future table impossible to ship silently.

**Contract**: `@Transactional deleteAccount(UUID userId)`: inject `List<PerUserDataDeleter>`,
invoke each, then delete the `User`. The plain FK on `ai_memory` means a missing
deleter surfaces as an FK violation on the final user delete, not an orphan. Expose
`Set<String> registeredTables()` for the guard test.

#### 6. Categories read endpoint

**File**: `backend/src/main/java/com/thedariusz/todoai/category/CategoryController.java`

**Intent**: Expose the frozen 11 domains so the frontend nav is data-driven (S-02
needs this too).

**Contract**: `GET /api/categories` → `{ items: [ {code, name_pl, display_order} ] }`
(top-level object, #110) sorted by `display_order`, from `CategoryRepository`. snake_case
via the global Jackson strategy; `code` values are UPPER_SNAKE (`x-extensible-enum`, #112).
Authenticated, read-only. **Not paginated** — 11 fixed rows (a documented #159/#226
exception).

#### 7. Error handling

**File**: `backend/src/main/java/com/thedariusz/todoai/` (a
`@RestControllerAdvice`)

**Intent**: Map failures to clean status codes and **Problem JSON** bodies without leaking
which emails exist on login.

**Contract**: all error bodies are **Problem JSON / RFC 9457** via Spring's `ProblemDetail`
(#176, `application/problem+json`); validation → **422** (#220); bad credentials → generic
**401**; duplicate email on register → 409 (accepted enumeration tradeoff — no email infra
to avoid it, and users need to know to log in instead). Never leak stack traces (#177;
`server.error.include-stacktrace` stays at its `never` default).

### Success Criteria

#### Automated Verification

- Auth-endpoint REST Assured tests pass (register 201/409, login 201/401, me 200/401, logout 204, delete 204): `cd backend && mvn test`
- Error bodies are Problem JSON (`application/problem+json`); validation → 422
- Security behavior verified: gated route → 401; a mutating request without a CSRF token → 403; with token → passes
- Testcontainers integration test proves **per-user isolation**: two users' `ai_memory` rows, `findByUserId` returns only the caller's
- Testcontainers integration test proves **FR-019 completeness**: register → delete → `app_user` and the user's `ai_memory` (+ facts/episodes) are gone
- **Orphan-guard** test passes: every table with a `user_id` column has a registered `PerUserDataDeleter`
- `GET /api/categories` returns `{ items: [...] }` — the 11 domains in `display_order`

#### Manual Verification

- Full lifecycle via curl/HTTPie against a running backend: register → login (cookie set) → me → delete (password re-auth) → me returns 401
- A second registration with the same email returns 409
- After delete, the email can be registered again

**Implementation Note**: Pause for manual confirmation before Phase 3.

---

## Phase 3: Frontend plumbing + auth screens (core S-01 outcome)

### Overview

Replace the demo scaffold with the real app: routing, a CSRF-aware API client, auth
context, login/register screens, a protected-route guard, logout, and confirmed
delete-account. **This phase alone satisfies the core S-01 outcome.**

### Changes Required

#### 1. Router + React Router dependency

**File**: `frontend/package.json`, `frontend/src/main.tsx`, `frontend/src/App.tsx`

**Intent**: Add `react-router-dom` and make the router the app root, retiring the
demo `App.tsx`.

**Contract**: Routes — `/login`, `/register` (public), and protected routes behind a
guard. Update/replace `App.test.tsx` accordingly.

#### 2. CSRF-aware API client

**File**: `frontend/src/api/client.ts`

**Intent**: One place that talks to `/api`, sends the session cookie, and echoes
the CSRF token on mutations — the plumbing every later slice reuses.

**Contract**: fetch wrapper with `credentials: 'include'`; on `POST/PUT/PATCH/DELETE`
reads the `XSRF-TOKEN` cookie and sets the `X-XSRF-TOKEN` header; centralized
handling that surfaces a 401 to the auth layer (clear user → redirect to login).

#### 3. Auth context

**File**: `frontend/src/auth/AuthContext.tsx`, `src/auth/useAuth.ts`

**Intent**: Hold the current user + status and expose the auth actions; bootstrap
from the server on load.

**Contract**: context value `{ user, status }` + `login`, `register`, `logout`,
`deleteAccount`. On mount, calls `GET /auth/me` (also priming the CSRF cookie);
status transitions `loading → authenticated | anonymous`.

#### 4. Protected-route guard

**File**: `frontend/src/auth/ProtectedRoute.tsx`

**Intent**: Redirect unauthenticated users to `/login`, remembering the intended
destination.

**Contract**: while `loading`, render nothing/spinner; when `anonymous`, redirect to
`/login` carrying the attempted location; after login, return there.

#### 5. Login + Register screens

**File**: `frontend/src/pages/Login.tsx`, `src/pages/Register.tsx`

**Intent**: The email+password forms.

**Contract**: client-side validation (email format, min-8 password) mirroring the
server; generic error display on 401; register maps 409 → "email already in use, log
in instead"; success routes to the app (login) / to `/login` (register).

#### 6. Logout + delete-account controls

**File**: `frontend/src/auth/` (a menu/action + a confirm dialog)

**Intent**: Wire the two session-ending actions; delete is irreversible so it is
double-gated.

**Contract**: logout calls `POST /auth/logout` then clears context. Delete requires a
confirm dialog **plus password re-entry** (matching the server re-auth), calls
`DELETE /auth/account`, then routes to a goodbye/login screen.

### Success Criteria

#### Automated Verification

- Typecheck + build pass: `cd frontend && npm run build`
- Lint passes: `cd frontend && npm run lint`
- RTL: login + register forms submit and show generic errors; `npm test`
- RTL: `ProtectedRoute` redirects an anonymous user to `/login`
- RTL: the API client echoes `X-XSRF-TOKEN` from the cookie on a mutating call and omits it on GET

#### Manual Verification

- Against `npm run dev` + running backend: register → redirected to login → login → land in the app; refresh keeps you logged in (`/me` bootstrap)
- Logout returns you to `/login` and gated routes redirect again
- Delete account (confirm + password) removes the account and returns to login; the email can be re-registered
- The session cookie is `HttpOnly`; the `XSRF-TOKEN` cookie is present and readable

**Implementation Note**: Pause for manual confirmation before Phase 4.

---

## Phase 4: Frontend fuller shell (layout + 11-domain nav)

### Overview

The opted-in shell: app chrome and a **data-driven** navigation over the frozen 11
life domains, with placeholder per-domain content. Pre-builds nothing whose shape is
still undecided — only the fixed domain *list*.

### Changes Required

#### 1. App layout chrome

**File**: `frontend/src/layout/AppLayout.tsx`

**Intent**: The authenticated shell — header (user email + logout/delete menu), a
nav region, and a content outlet — wrapping the protected routes.

**Contract**: renders inside `ProtectedRoute`; hosts `DomainNav` + a React Router
`<Outlet/>`.

#### 2. Domain navigation (data-driven)

**File**: `frontend/src/nav/DomainNav.tsx`

**Intent**: List the 11 domains from the server, ordered canonically, each linking to
a placeholder route — future slices replace the placeholder.

**Contract**: fetches `GET /api/categories` and reads `items`, renders sorted by
`display_order`, links to `/domain/:code`.

#### 3. Placeholder domain page

**File**: `frontend/src/pages/DomainPlaceholder.tsx`

**Intent**: A holding page so the nav is navigable before feature slices exist.

**Contract**: reads `:code`, shows the domain name + "coming in a later slice."

### Success Criteria

#### Automated Verification

- Typecheck + build + lint pass: `cd frontend && npm run build && npm run lint`
- RTL: `DomainNav` renders 11 items in `display_order` from a mocked `/categories`; `npm test`

#### Manual Verification

- After login, the shell shows the header + all 11 domains in canonical order
- Clicking a domain routes to its placeholder page; the header/logout/delete work from the shell

**Implementation Note**: Final phase — after this, run the full `/check` gate.

---

## Testing Strategy

> **HTTP-layer tests use REST Assured** (`io.rest-assured:rest-assured`, pinned `6.0.1`)
> against a random-port embedded server (`@SpringBootTest(webEnvironment = RANDOM_PORT)`), so the
> real Spring Security filter chain is exercised end-to-end. **MockMvc / `spring-security-test`
> are not used.** The Phase 2 auth lifecycle (register → login → cookie + CSRF → gated call →
> delete) is a natural REST Assured flow using a session/cookie filter to carry the session and
> echo the `X-XSRF-TOKEN`.

### Unit Tests

- `Email` value object: normalization + format rejection.
- `User` aggregate: constructor invariant (no user without email/hash).
- `RegistrationService`: duplicate-email rejection; user + ai_memory created together.
- `AccountDeletionService`: invokes every registered deleter then deletes the user.
- Frontend: API-client CSRF echo; auth context state transitions.

### Integration Tests (Testcontainers)

- Migrations V1–V5 apply on a fresh DB; `ddl-auto=validate` passes.
- **Isolation**: `findByUserId` returns only the caller's `ai_memory`.
- **FR-019 completeness**: after delete, `app_user` + the user's `ai_memory`
  (facts + episodes) are gone.
- **Orphan-guard**: every `user_id`-bearing table has a registered deleter.
- **Security**: 401 on gated route; 403 on missing CSRF; success with CSRF + auth
  (REST Assured over real HTTP — a real login establishes the session cookie, not a mock user).

### Manual Testing Steps

1. Register a new account; confirm a taken email returns a clear "already in use".
2. Log in; confirm the session cookie is `HttpOnly; SameSite=Strict` and survives a
   refresh; the `XSRF-TOKEN` cookie is present.
3. Navigate gated routes logged out → redirect to login → return after login.
4. Delete account (confirm + password) → session gone, email re-registerable.
5. Confirm the 11-domain nav renders in canonical order and each placeholder opens.

## Performance Considerations

Negligible at 1–10 users / <1 qps. The only DB touches are user-initiated
(register/login/me/delete/categories) and rare — consistent with the Neon idleness
rule; **session validation stays in-memory and never queries the DB**, so autosuspend
is preserved. BCrypt cost is a one-off per login/register.

## Migration Notes

- `V4` (create `app_user`) and `V5` (add FK) are expand-only and safe under image
  rollback: `ai_memory` is empty (no F-02 writers), so the FK can't fail on existing
  data, and Hibernate `validate` on an older image ignores the extra table/constraint.
- Table is **`app_user`**, not `user` (reserved word). Update `data-model.md` and the
  `V3` comment's "`user(id)`" prose to `app_user(id)` when next touched.
- Prod cutover: set the Fly secret/env `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`
  (dev leaves it false — Vite serves http).

## References

- **API contract (authoritative, Zalando-aligned):** `context/changes/account-and-auth/openapi.yaml`
- Decided session model: `context/foundation/auth-session-model.md`
- Roadmap slice: `context/foundation/roadmap.md` (S-01)
- Data model + deferred FK: `context/foundation/data-model.md`
- Aggregate pattern to mirror: `backend/.../ai/memory/AiMemory.java`
- Deferred-FK note: `backend/.../db/migration/V3__create_ai_memory.sql:14-17`
- Neon idleness rule: `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend security foundation + User aggregate + migrations

#### Automated

- [x] 1.1 Backend compiles + mappings validate against the migrated schema — 14d3e5f
- [x] 1.2 Migrations V1–V5 apply cleanly on a fresh Testcontainers DB — 14d3e5f
- [x] 1.3 Existing suite stays green, incl. the updated ping test — 14d3e5f
- [x] 1.4 Gated request → 401, permitAll request → 200 (SecurityGatewayApiTest, REST Assured) — 14d3e5f
- [x] 1.5 Convention held: `AiMemory implements UserOwned`, read via the scoped finder — 14d3e5f

#### Manual

- [x] 1.6 `spring-boot:run` boots with Security enabled and no schema-drift error — 14d3e5f
- [x] 1.7 Gated `/api/*` → 401 (not HTML redirect); `/api/ping` + `/actuator/health/liveness` → 200 — 14d3e5f

### Phase 2: Backend auth flows + FR-019 deletion + isolation + categories

#### Automated

- [x] 2.1 Auth-endpoint REST Assured tests pass (register/login/me/logout/delete) — a435ecc
- [x] 2.2 Security behavior verified: gated → 401; no-CSRF mutation → 403; with token → passes — a435ecc
- [x] 2.3 Integration test proves per-user isolation (findByUserId scoping) — a435ecc
- [x] 2.4 Integration test proves FR-019 completeness (user + ai_memory gone) — a435ecc
- [x] 2.5 Orphan-guard test passes (every user_id table has a deleter) — a435ecc
- [x] 2.6 `GET /api/categories` returns `{items:[...]}` — 11 domains in display_order — a435ecc

#### Manual

- [x] 2.7 Full lifecycle via curl: register → login → me → delete → me 401 — a435ecc
- [x] 2.8 Duplicate registration returns 409 — a435ecc
- [x] 2.9 After delete, the email can be registered again — a435ecc

### Phase 3: Frontend plumbing + auth screens

#### Automated

- [ ] 3.1 Typecheck + build pass (`npm run build`)
- [ ] 3.2 Lint passes (`npm run lint`)
- [ ] 3.3 RTL: login + register forms submit and show generic errors
- [ ] 3.4 RTL: ProtectedRoute redirects an anonymous user to `/login`
- [ ] 3.5 RTL: API client echoes `X-XSRF-TOKEN` on mutations, omits on GET

#### Manual

- [ ] 3.6 register → login → land in app; refresh stays logged in
- [ ] 3.7 Logout returns to `/login`; gated routes redirect again
- [ ] 3.8 Delete account (confirm + password) → back to login; email re-registerable
- [ ] 3.9 Session cookie is HttpOnly; XSRF-TOKEN cookie present + readable

### Phase 4: Frontend fuller shell

#### Automated

- [ ] 4.1 Typecheck + build + lint pass
- [ ] 4.2 RTL: DomainNav renders 11 items in display_order from a mocked `/categories`

#### Manual

- [ ] 4.3 Shell shows header + all 11 domains in canonical order after login
- [ ] 4.4 Clicking a domain routes to its placeholder; header/logout/delete work from the shell

# Developer Docs Site Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A single self-contained `docs/index.html` developer-documentation SPA for 2do AI: architecture, class diagrams, sequence diagrams, glossary, CI/CD.

**Architecture:** One HTML file with a sidebar of hash links; a ~15-line JS router shows one `<section>` at a time and lazily renders that section's Mermaid diagrams (Mermaid mis-measures text inside `display:none`/`hidden` containers, so rendering must happen after a section becomes visible). Diagrams are Mermaid text in `<pre class="mermaid">` blocks. Data-model diagrams are NOT redrawn — the existing drawio SVG exports are embedded via relative `<img>` paths.

**Tech Stack:** Plain HTML/CSS/JS, Mermaid 11 as an ES module from jsdelivr CDN. No build step, no npm, no framework.

**Spec:** `docs/superpowers/specs/2026-07-29-dev-docs-site-design.md`

## Global Constraints

- Everything lives in ONE file: `docs/index.html`. No other files may be created (besides this plan/spec already committed).
- Must work opened via `file://` — no dev server required. (jsdelivr serves `Access-Control-Allow-Origin: *`, so the ESM import works from `file://`.)
- Mermaid from CDN: `https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs`. Internet required to view — accepted in spec.
- Planned/target content must ALWAYS be visually labeled with the `badge-planned` badge defined in Task 1; implemented content uses `badge-live`. Never mix unlabeled.
- Later tasks insert content at two markers created in Task 1: nav links go before `<!-- nav-end -->`, sections before `<!-- sections-end -->`. Section `id` must equal the nav link's hash.
- **Verification procedure (referenced by every task as "Verify section `<id>`"):** open `docs/index.html` in a browser, navigate to `index.html#<id>`, then run in the console:
  ```js
  ({shown: !document.getElementById(location.hash.slice(1)).hidden,
    unrendered: document.querySelectorAll(`#${location.hash.slice(1)} pre.mermaid:not([data-processed])`).length,
    errors: document.querySelectorAll('svg[aria-roledescription="error"]').length})
  ```
  Expected: `{shown: true, unrendered: 0, errors: 0}`. A Mermaid syntax error renders an error SVG — `errors` must be 0.
- Commits: conventional messages, `docs(site): …`. Work on branch `docs/dev-docs-site` cut from `master` (the current `refactor/auth-simplify` branch has unrelated uncommitted changes — do not touch them).
- Facts in the docs were derived from the code on 2026-07-29 (see per-task source-file lists). If a source file contradicts this plan at execution time, the source file wins — update the diagram text accordingly and note it in the commit message.

---

### Task 1: Skeleton SPA + Overview section

**Files:**
- Create: `docs/index.html`

**Interfaces:**
- Produces: the page shell every later task inserts into — markers `<!-- nav-end -->` and `<!-- sections-end -->`, CSS classes `badge-live`, `badge-planned`, `card`, and the lazy Mermaid router. Later tasks rely on exactly these names.

- [ ] **Step 1: Check out the branch**

The `docs/dev-docs-site` branch already exists (cut from `master`, carrying the spec and this plan):

```bash
git switch docs/dev-docs-site
```

- [ ] **Step 2: Write `docs/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>2do AI — Developer Docs</title>
<style>
  :root { --ink:#1a1a2e; --muted:#6b7280; --line:#e5e7eb; --accent:#4f46e5; --bg:#fafafa; }
  * { box-sizing:border-box; }
  body { margin:0; font:16px/1.6 system-ui, sans-serif; color:var(--ink); background:var(--bg); display:flex; min-height:100vh; }
  nav { width:230px; flex-shrink:0; border-right:1px solid var(--line); padding:1.5rem 0; position:sticky; top:0; height:100vh; background:#fff; }
  nav h1 { font-size:1.05rem; padding:0 1.25rem .75rem; margin:0; }
  nav a { display:block; padding:.45rem 1.25rem; color:var(--muted); text-decoration:none; border-left:3px solid transparent; }
  nav a.active { color:var(--accent); border-left-color:var(--accent); background:#eef2ff; }
  main { flex:1; padding:2rem 3rem; max-width:1000px; }
  section[hidden] { display:none; }
  h2 { margin-top:0; } h3 { margin-top:2rem; }
  table { border-collapse:collapse; width:100%; } th,td { border:1px solid var(--line); padding:.5rem .75rem; text-align:left; background:#fff; }
  code { background:#eef; padding:.1em .35em; border-radius:4px; font-size:.9em; }
  pre.mermaid { background:#fff; border:1px solid var(--line); border-radius:8px; padding:1rem; overflow-x:auto; }
  img.diagram { max-width:100%; background:#fff; border:1px solid var(--line); border-radius:8px; }
  .badge { display:inline-block; font-size:.72rem; font-weight:600; padding:.1em .6em; border-radius:99px; vertical-align:middle; }
  .badge-live { background:#dcfce7; color:#166534; }
  .badge-planned { background:#fef3c7; color:#92400e; }
  .card { background:#fff; border:1px solid var(--line); border-radius:8px; padding:1rem 1.25rem; margin:.75rem 0; }
  .note { color:var(--muted); font-size:.9rem; }
</style>
</head>
<body>
<nav>
  <h1>2do AI · Dev Docs</h1>
  <a href="#overview">Overview</a>
  <!-- nav-end -->
</nav>
<main>
<!-- HOW TO UPDATE THESE DOCS: hand-edited single file. Diagrams are Mermaid text in
     <pre class="mermaid"> blocks — edit the text, reload the page. Add a section by
     inserting a <section id="x" hidden> before the sections-end marker and a matching
     <a href="#x"> before the nav-end marker. Keep planned content labeled badge-planned. -->

<section id="overview" hidden>
  <h2>Overview <span class="badge badge-live">implemented</span></h2>
  <p><strong>2do AI</strong> is a personal AI-powered todo + planning app: three task layers
  (<em>current</em>, <em>long-term</em>, <em>dreams</em>) across 11 life domains, with proactive
  natural-rhythm reminders. Solo-dev, after-hours MVP.</p>
  <p>The repo holds <strong>two independent projects</strong> — no monorepo tooling, each builds
  from its own directory:</p>
  <table>
    <tr><th></th><th>Stack</th><th>Role</th></tr>
    <tr><td><code>backend/</code></td><td>Spring Boot 4, Java 25, Maven, PostgreSQL 18 (JPA/Hibernate + Flyway)</td><td>REST API, auth, AI orchestration, background jobs</td></tr>
    <tr><td><code>frontend/</code></td><td>React 19, Vite, TypeScript (strict), PWA</td><td>SPA consuming the REST API</td></tr>
  </table>
  <h3>Where things live</h3>
  <table>
    <tr><td><code>context/foundation/</code></td><td>Design docs: PRD, roadmap, data model, auth session model, deployment runbook</td></tr>
    <tr><td><code>backend/src/main/resources/db/migration/</code></td><td>Flyway migrations (schema owner; Hibernate runs <code>ddl-auto=validate</code>)</td></tr>
    <tr><td><code>.github/workflows/</code></td><td>CI/CD (see the CI/CD section)</td></tr>
  </table>
  <p class="note">These docs are for developers. A separate user-facing document will exist later.</p>
</section>
<!-- sections-end -->
</main>
<script type="module">
import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
mermaid.initialize({ startOnLoad: false, theme: 'neutral' });
const sections = document.querySelectorAll('main > section');
async function route() {
  const id = location.hash.slice(1) || 'overview';
  sections.forEach(s => s.hidden = (s.id !== id));
  document.querySelectorAll('nav a').forEach(a =>
    a.classList.toggle('active', a.getAttribute('href') === '#' + id));
  // Render Mermaid only after the section is visible: Mermaid measures text via getBBox,
  // which returns 0 inside hidden containers and silently produces broken diagrams.
  const pending = document.querySelectorAll('#' + CSS.escape(id) + ' pre.mermaid:not([data-processed])');
  if (pending.length) await mermaid.run({ nodes: pending });
}
addEventListener('hashchange', route);
route();
</script>
</body>
</html>
```

- [ ] **Step 3: Verify section `overview`** (Global Constraints procedure). Also confirm the sidebar link highlights.

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "docs(site): SPA skeleton with hash router and Overview section"
```

---

### Task 2: Architecture section

**Files:**
- Modify: `docs/index.html` (insert at the two markers)

**Interfaces:**
- Consumes: markers, badges, router from Task 1.
- Sources of truth: `CLAUDE.md` (Deployment Pattern B), `context/foundation/deployment.md`, `context/foundation/infrastructure.md`, `context/foundation/ai-provider.md`.

- [ ] **Step 1: Insert nav link** before `<!-- nav-end -->`:

```html
  <a href="#architecture">Architecture</a>
```

- [ ] **Step 2: Insert section** before `<!-- sections-end -->`:

```html
<section id="architecture" hidden>
  <h2>Architecture</h2>
  <h3>Current <span class="badge badge-live">implemented</span></h3>
  <p>Pattern B — <strong>unified origin</strong>: Cloudflare Pages serves the static frontend and
  reverse-proxies <code>/api/*</code> to the Fly.io backend, so production is same-origin and needs
  no CORS. Sessions are server-side cookies held <strong>in-memory</strong> on the single Fly
  machine (not JWT, never DB-backed — a per-request session <code>SELECT</code> would defeat Neon
  autosuspend).</p>
  <pre class="mermaid">
flowchart LR
  U[Browser] --> CFP[Cloudflare Pages<br/>React 19 PWA static build]
  U -- "/api/* (same origin)" --> PRX[Cloudflare reverse proxy] --> BE[Fly.io — Spring Boot 4 API<br/>single machine, in-memory sessions]
  BE -- "JPA / Hibernate (validate)" --> PG[(Neon PostgreSQL 18<br/>pooled + SSL, autosuspend)]
  BE -- "Flyway migrations on boot" --> PG
  GHA[GitHub Actions] -- "flyctl deploy" --> BE
  GHA -- "wrangler pages deploy" --> CFP
  </pre>
  <h3>Target <span class="badge badge-planned">planned</span></h3>
  <p>The MVP adds AI orchestration (task classification into life domains via a
  JSON-schema-constrained LLM call) and background reminder jobs on the same single backend.</p>
  <pre class="mermaid">
flowchart LR
  U[Browser PWA] -- "/api/*" --> BE[Spring Boot 4 API]
  BE --> PG[(Neon PostgreSQL 18)]
  BE -- "LlmClient (Spring AI)" --> LLM[AI provider API<br/>JSON-schema constrained]
  BE --> SCH[Scheduled jobs<br/>natural-rhythm reminders] --> U
  </pre>
  <h3>Data model</h3>
  <p>Canonical ER diagrams (drawio exports, not redrawn here):</p>
  <p><span class="badge badge-live">current</span></p>
  <img class="diagram" src="../context/foundation/data-model-current.svg" alt="Current data model">
  <p><span class="badge badge-planned">target</span></p>
  <img class="diagram" src="../context/foundation/data-model-target.svg" alt="Target data model">
  <p class="note">Sources: <code>context/foundation/data-model.md</code>, <code>*.drawio</code> files alongside the SVGs.</p>
</section>
```

- [ ] **Step 3: Verify section `architecture`** (2 diagrams render, both SVG images load — check `document.querySelectorAll('#architecture img.diagram')` both have `naturalWidth > 0`).

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "docs(site): architecture section — current and target diagrams"
```

---

### Task 3: Backend design section (class diagrams per slice)

**Files:**
- Modify: `docs/index.html` (insert at the two markers)

**Interfaces:**
- Consumes: markers, badges, router from Task 1.
- Sources of truth: `backend/src/main/java/com/thedariusz/todoai/` — packages `user`, `auth`, `security`, `session`, `account`, `category`, `ai`, `ai/memory`. Verify class names against the code before committing.

- [ ] **Step 1: Insert nav link** before `<!-- nav-end -->`:

```html
  <a href="#backend">Backend design</a>
```

- [ ] **Step 2: Insert section** before `<!-- sections-end -->`:

```html
<section id="backend" hidden>
  <h2>Backend design <span class="badge badge-live">implemented</span></h2>
  <p>Base package <code>com.thedariusz.todoai</code>, one package per domain slice. Aggregates use
  UUID v7 surrogate PKs (<code>@UuidGenerator(style = VERSION_7)</code>); every domain table has
  <code>created_at</code>/<code>updated_at</code> audit columns.</p>

  <h3>user + auth</h3>
  <pre class="mermaid">
classDiagram
  class User {
    <<aggregate root>>
    UUID id
    Email email
    String passwordHash
  }
  class Email {
    <<value object>>
    +of(String) Email
  }
  class UserRepository {
    <<Spring Data JPA>>
  }
  class UserController {
    +register(RegisterRequest) 201
    +currentUser() UserResponse
    +deleteCurrentUser(DeleteAccountRequest) 204
  }
  class RegistrationService {
    +register(email, password) User
  }
  User *-- Email
  UserRepository --> User
  UserController --> RegistrationService
  UserController --> AccountDeletionService
  RegistrationService --> UserRepository
  RegistrationService --> AiMemoryRepository : creates AiMemory root (same tx)
  </pre>

  <h3>security + session</h3>
  <pre class="mermaid">
classDiagram
  class SecurityConfig {
    <<Configuration>>
    filter chain + auth beans
  }
  class AppUserDetailsService {
    +loadUserByUsername(email) UserPrincipal
  }
  class UserPrincipal {
    <<record UserDetails>>
    UUID userId
    String email
    String passwordHash
  }
  class CsrfCookieFilter {
    materializes XSRF-TOKEN cookie
  }
  class ProblemDetailsSecurityHandler {
    401 and 403 as Problem JSON
  }
  class SessionController {
    +login(LoginRequest) 201
    +logout() 204
  }
  SecurityConfig --> AppUserDetailsService
  SecurityConfig --> CsrfCookieFilter
  SecurityConfig --> ProblemDetailsSecurityHandler
  AppUserDetailsService --> UserRepository
  AppUserDetailsService ..> UserPrincipal : creates
  SessionController --> SecurityConfig : uses chain beans (AuthenticationManager, LogoutHandler, ...)
  </pre>

  <h3>account (FR-019 deletion)</h3>
  <pre class="mermaid">
classDiagram
  class AccountDeletionService {
    +deleteAccount(UUID userId) [Transactional]
  }
  class PerUserDataDeleter {
    <<interface>>
    +deleteAllForUser(UUID)
  }
  class AiMemoryDataDeleter
  AccountDeletionService o-- PerUserDataDeleter : all registered beans
  AccountDeletionService --> UserRepository : delete(user) last — FK fails loudly if a deleter is missing
  PerUserDataDeleter <|.. AiMemoryDataDeleter
  </pre>

  <h3>category</h3>
  <pre class="mermaid">
classDiagram
  class LifeDomain {
    <<enumeration>>
    HEALTH ... INNER_GROWTH (11 values)
  }
  class Category {
    <<reference table>>
    String code — natural PK
    String namePl
    int displayOrder
  }
  class CategoryController {
    +list() GET /api/categories
  }
  class CategorySyncCheck {
    <<ApplicationRunner>>
    fails boot on enum/table drift
  }
  CategoryController --> CategoryRepository
  CategorySyncCheck --> CategoryRepository
  CategorySyncCheck ..> LifeDomain : compares codes
  </pre>

  <h3>ai + ai/memory</h3>
  <pre class="mermaid">
classDiagram
  class LlmClient {
    <<interface>>
    port to the LLM provider
  }
  class SpringAiLlmClient {
    <<adapter>>
  }
  class JsonSchema {
    constrains structured output
  }
  class AiMemory {
    <<aggregate root>>
    UUID id
    UUID userId
  }
  class Episode {
    jsonb payload
  }
  class AiMemoryRenderer {
    renders memory into prompt text
  }
  LlmClient <|.. SpringAiLlmClient
  SpringAiLlmClient --> JsonSchema
  AiMemory *-- Episode
  AiMemory *-- ProfileFact
  AiMemoryRepository --> AiMemory
  AiMemoryRenderer --> AiMemory
  </pre>
  <p class="note">Exact endpoints: <code>POST /api/users</code>, <code>GET /api/users/me</code>,
  <code>DELETE /api/users/me</code>, <code>POST /api/sessions</code>,
  <code>DELETE /api/sessions/current</code>, <code>GET /api/categories</code>,
  <code>GET /api/ping</code>.</p>
</section>
```

- [ ] **Step 3: Cross-check against the code.** Open the listed packages; if any class/method name in the diagrams drifted from the source, fix the diagram text (source wins — Global Constraints).

- [ ] **Step 4: Verify section `backend`** (5 diagrams render, `errors: 0`).

- [ ] **Step 5: Commit**

```bash
git add docs/index.html
git commit -m "docs(site): backend class diagrams per slice"
```

---

### Task 4: Flows section — implemented sequence diagrams

**Files:**
- Modify: `docs/index.html` (insert at the two markers)

**Interfaces:**
- Consumes: markers, badges, router from Task 1. Task 5 appends the planned flows INSIDE this same section, before its closing `</section>` tag, at the marker `<!-- planned-flows -->` created here.
- Sources of truth: `SessionController.java`, `UserController.java`, `RegistrationService.java`, `AccountDeletionService.java`, `CategorySyncCheck.java`.

- [ ] **Step 1: Insert nav link** before `<!-- nav-end -->`:

```html
  <a href="#flows">Flows</a>
```

- [ ] **Step 2: Insert section** before `<!-- sections-end -->`:

```html
<section id="flows" hidden>
  <h2>Flows</h2>
  <h3>Registration <span class="badge badge-live">implemented</span></h3>
  <pre class="mermaid">
sequenceDiagram
  autonumber
  actor SPA
  participant UC as UserController
  participant RS as RegistrationService
  participant UR as UserRepository
  participant MR as AiMemoryRepository
  SPA->>UC: POST /api/users {email, password}
  UC->>RS: register(email, password)
  Note over RS: BCrypt-encode password,<br/>new User(Email.of(email), hash)
  RS->>UR: saveAndFlush(user)
  alt UNIQUE(app_user.email) violated
    UR-->>RS: DataIntegrityViolationException
    RS-->>SPA: 409 Problem JSON (EmailAlreadyRegistered)
  else created
    RS->>MR: save(new AiMemory(userId))
    Note over RS,MR: one @Transactional — user + memory atomic
    RS-->>UC: User
    UC-->>SPA: 201 Created, Location: /api/users/me
  end
  Note over SPA: registration does NOT log in —<br/>POST /api/sessions is the only session-creation path
  </pre>

  <h3>Login (session creation) <span class="badge badge-live">implemented</span></h3>
  <pre class="mermaid">
sequenceDiagram
  autonumber
  actor SPA
  participant SC as SessionController
  participant AM as AuthenticationManager
  participant UDS as AppUserDetailsService
  SPA->>SC: POST /api/sessions {email, password}
  SC->>AM: authenticate(unauthenticated token)
  AM->>UDS: loadUserByUsername(email)
  UDS-->>AM: UserPrincipal (userId, email, passwordHash)
  alt bad credentials (unknown email OR wrong password)
    AM-->>SPA: 401 Problem JSON — identical either way,<br/>reveals nothing about which emails exist
  else authenticated
    SC->>SC: SessionAuthenticationStrategy.onAuthentication()<br/>rotates session id (fixation) + CSRF token
    SC->>SC: materializeRotatedCsrfToken() — forces new<br/>XSRF-TOKEN cookie onto THIS response
    SC->>SC: SecurityContextRepository.saveContext()<br/>— actually writes the session
    SC-->>SPA: 201 Created + Set-Cookie (session, XSRF-TOKEN)
  end
  </pre>

  <h3>Authenticated request <span class="badge badge-live">implemented</span></h3>
  <pre class="mermaid">
sequenceDiagram
  actor SPA
  participant FC as Security filter chain
  participant UC as UserController
  SPA->>FC: GET /api/users/me + session cookie
  Note over FC: session validated in-memory —<br/>no DB query, Neon stays suspended
  alt missing/expired session
    FC-->>SPA: 401 Problem JSON (ProblemDetailsSecurityHandler)
  else valid session
    FC->>UC: currentUser(@AuthenticationPrincipal UserPrincipal)
    UC-->>SPA: 200 UserResponse — answered from the principal, no query
  end
  </pre>

  <h3>Logout <span class="badge badge-live">implemented</span></h3>
  <pre class="mermaid">
sequenceDiagram
  actor SPA
  participant SC as SessionController
  participant LH as LogoutHandler
  SPA->>SC: DELETE /api/sessions/current (+ X-XSRF-TOKEN header)
  SC->>LH: logout(request, response, authentication)
  Note over LH: invalidates the HttpSession,<br/>clears context + cookies
  SC-->>SPA: 204 No Content
  </pre>

  <h3>Account deletion (FR-019) <span class="badge badge-live">implemented</span></h3>
  <pre class="mermaid">
sequenceDiagram
  autonumber
  actor SPA
  participant UC as UserController
  participant ADS as AccountDeletionService
  participant D as every PerUserDataDeleter bean
  participant UR as UserRepository
  participant SR as SessionRegistry
  SPA->>UC: DELETE /api/users/me {password}
  UC->>UC: passwordEncoder.matches(password, principal.passwordHash)<br/>— re-auth from the principal, no query
  alt password mismatch
    UC-->>SPA: 403 Problem JSON (session still valid — not 401)
  else verified
    UC->>ADS: deleteAccount(userId) — @Transactional
    ADS->>D: deleteAllForUser(userId)
    ADS->>UR: delete(user) — plain FKs (no ON DELETE) fail loudly<br/>if a module forgot to register a deleter
    UC->>SR: expire all sibling sessions (phone left logged in)
    UC->>UC: logout current session — a failure here is logged,<br/>never surfaced: the deletion already committed
    UC-->>SPA: 204 No Content
  end
  </pre>

  <h3>Startup category sync check <span class="badge badge-live">implemented</span></h3>
  <pre class="mermaid">
sequenceDiagram
  participant Boot as Spring Boot startup
  participant CSC as CategorySyncCheck (ApplicationRunner)
  participant CR as CategoryRepository
  Boot->>CSC: run(args)
  CSC->>CR: findAll()
  CSC->>CSC: compare category.code set with LifeDomain enum names
  alt drift (missing / extra / renamed code)
    CSC-->>Boot: IllegalStateException — boot fails fast
  else in sync
    CSC-->>Boot: ok
  end
  </pre>
  <!-- planned-flows -->
</section>
```

- [ ] **Step 3: Verify section `flows`** (6 diagrams render, `errors: 0`).

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "docs(site): sequence diagrams for all implemented flows"
```

---

### Task 5: Planned flows + Roadmap section

**Files:**
- Modify: `docs/index.html` (planned flows go at `<!-- planned-flows -->` inside `#flows`; roadmap section at the usual markers)

**Interfaces:**
- Consumes: `<!-- planned-flows -->` marker from Task 4; nav/section markers from Task 1.
- Sources of truth: `context/foundation/prd.md`, `context/foundation/roadmap.md`, `context/foundation/ai-provider.md`.

- [ ] **Step 1: Insert at `<!-- planned-flows -->`** (keep the marker below the inserted content):

```html
  <h3>Task creation with AI classification <span class="badge badge-planned">planned</span></h3>
  <pre class="mermaid">
sequenceDiagram
  actor SPA
  participant TC as TaskController (planned)
  participant TS as TaskService (planned)
  participant LLM as LlmClient → AI provider
  participant PG as Postgres
  SPA->>TC: POST /api/tasks {text}
  TC->>TS: create(userId, text)
  TS->>LLM: classify text — JSON-schema-constrained<br/>output: {lifeDomain, taskLayer}
  LLM-->>TS: {domain: HEALTH, layer: CURRENT}
  TS->>PG: insert task (uuid v7, audit columns)
  TC-->>SPA: 201 Created
  </pre>

  <h3>Natural-rhythm reminder <span class="badge badge-planned">planned</span></h3>
  <pre class="mermaid">
sequenceDiagram
  participant SCH as Scheduled job (backend)
  participant PG as Postgres
  participant LLM as LlmClient
  participant U as User (PWA notification)
  SCH->>PG: load due tasks + AiMemory for user
  SCH->>LLM: pick what to surface, phrase the nudge<br/>(AiMemoryRenderer supplies context)
  LLM-->>SCH: reminder text
  SCH->>U: proactive reminder at a natural moment
  </pre>
```

- [ ] **Step 2: Insert nav link** before `<!-- nav-end -->`:

```html
  <a href="#roadmap">Roadmap / target</a>
```

- [ ] **Step 3: Insert section** before `<!-- sections-end -->`:

```html
<section id="roadmap" hidden>
  <h2>Roadmap / target state <span class="badge badge-planned">planned</span></h2>
  <p>What exists today is the account/auth slice plus scaffolding for AI memory and categories.
  The MVP roadmap builds vertical slices on top:</p>
  <div class="card"><strong>Tasks</strong> — CRUD across the three layers (current / long-term / dreams),
  AI classification into the 11 life domains at capture time.</div>
  <div class="card"><strong>AI orchestration</strong> — <code>LlmClient</code> port already exists with a
  Spring AI adapter; JSON-schema-constrained structured output; per-user <code>AiMemory</code>
  (profile facts + episodes) rendered into prompts.</div>
  <div class="card"><strong>Natural-rhythm reminders</strong> — background jobs that surface the right
  task at the right moment, not fixed-time alarms.</div>
  <p>Planned sequence diagrams live in the <a href="#flows">Flows</a> section (yellow badges).
  Target ER diagram is in <a href="#architecture">Architecture</a>.</p>
  <p class="note">Authoritative detail: <code>context/foundation/prd.md</code>,
  <code>context/foundation/roadmap.md</code>, <code>context/foundation/ai-provider.md</code>.</p>
</section>
```

- [ ] **Step 4: Verify sections `flows` (now 8 diagrams) and `roadmap`** (`errors: 0` on both).

- [ ] **Step 5: Commit**

```bash
git add docs/index.html
git commit -m "docs(site): planned flows and roadmap section"
```

---

### Task 6: Glossary section

**Files:**
- Modify: `docs/index.html` (insert at the two markers)

**Interfaces:**
- Consumes: markers from Task 1.
- Sources of truth: `context/foundation/prd.md` (product terms), `LifeDomain.java`, `CLAUDE.md`, `context/foundation/auth-session-model.md`.

- [ ] **Step 1: Insert nav link** before `<!-- nav-end -->`:

```html
  <a href="#glossary">Glossary</a>
```

- [ ] **Step 2: Insert section** before `<!-- sections-end -->`:

```html
<section id="glossary" hidden>
  <h2>Glossary</h2>
  <p>The ubiquitous language of the codebase — use these words in code, commits, and docs.</p>
  <table>
    <tr><th>Term</th><th>Meaning</th></tr>
    <tr><td><strong>Task layer</strong></td><td>One of three horizons a task lives in: <em>current</em> (actionable now), <em>long-term</em> (goals), <em>dreams</em> (aspirations). <span class="badge badge-planned">planned</span></td></tr>
    <tr><td><strong>Life domain</strong></td><td>One of 11 fixed categories every task belongs to: HEALTH, FINANCE, CAREER, EDUCATION, RELATIONSHIPS, HOME, LEISURE, ADMIN, SAFETY, TRANSPORT, INNER_GROWTH. Enum <code>LifeDomain</code> mirrors the seeded <code>category</code> table; <code>CategorySyncCheck</code> fails boot on drift.</td></tr>
    <tr><td><strong>Natural-rhythm reminder</strong></td><td>A proactive nudge surfaced at a contextually right moment rather than a fixed alarm time. <span class="badge badge-planned">planned</span></td></tr>
    <tr><td><strong>AiMemory</strong></td><td>Per-user aggregate the AI consults; created atomically with the <code>User</code> at registration ("every user has exactly one memory").</td></tr>
    <tr><td><strong>Episode</strong></td><td>One recorded AI interaction inside <code>AiMemory</code>; payload stored as <code>jsonb</code>.</td></tr>
    <tr><td><strong>Profile fact</strong></td><td>A durable statement about the user inside <code>AiMemory</code>, rendered into prompts by <code>AiMemoryRenderer</code>.</td></tr>
    <tr><td><strong>UserPrincipal</strong></td><td>The authenticated identity (record: userId, email, passwordHash) carried by the session — lets <code>/me</code> and re-auth answer without a DB query.</td></tr>
    <tr><td><strong>PerUserDataDeleter</strong></td><td>Interface each module implements so FR-019 account deletion erases its per-user rows; the final user delete's plain FKs fail loudly if one is missing.</td></tr>
    <tr><td><strong>Session model</strong></td><td>Server-side session cookie (<code>HttpOnly; Secure; SameSite=Strict</code>), held in-memory on the single Fly machine. Not JWT; never DB-backed (Neon autosuspend). Decided 2026-07-22.</td></tr>
    <tr><td><strong>Pattern B / unified origin</strong></td><td>Cloudflare serves the SPA and reverse-proxies <code>/api/*</code> to Fly — production is same-origin, no CORS.</td></tr>
    <tr><td><strong>Expand-only migration</strong></td><td>Flyway migrations must stay backward-compatible (safe under image rollback); destructive changes go expand → contract.</td></tr>
    <tr><td><strong>FR-nnn</strong></td><td>Functional requirement id from <code>context/foundation/prd.md</code> (e.g. FR-019 = account deletion, FR-007 = the 11 domains).</td></tr>
    <tr><td><strong>Problem JSON</strong></td><td>RFC 9457 error body used by every API error (Zalando guideline), including security 401/403 via <code>ProblemDetailsSecurityHandler</code>.</td></tr>
  </table>
</section>
```

- [ ] **Step 3: Verify section `glossary`** (renders, no Mermaid in it, nav highlights).

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "docs(site): glossary of ubiquitous language"
```

---

### Task 7: CI/CD & Deployment section

**Files:**
- Modify: `docs/index.html` (insert at the two markers)

**Interfaces:**
- Consumes: markers from Task 1.
- Sources of truth: `.github/workflows/deploy-backend.yml`, `.github/workflows/deploy-frontend.yml`, `context/foundation/deployment-runbook.md`.

- [ ] **Step 1: Insert nav link** before `<!-- nav-end -->`:

```html
  <a href="#cicd">CI/CD &amp; deployment</a>
```

- [ ] **Step 2: Insert section** before `<!-- sections-end -->`:

```html
<section id="cicd" hidden>
  <h2>CI/CD &amp; deployment <span class="badge badge-live">implemented</span></h2>
  <p>Two independent, <strong>path-filtered</strong> GitHub Actions workflows trigger on push to
  <code>master</code> — a frontend-only change never redeploys the JVM and vice versa.</p>
  <pre class="mermaid">
flowchart LR
  P[push to master] --> F{path filter}
  F -- "backend/**" --> B1[JDK 25 temurin<br/>mvn -B test] --> B2[record live image ref<br/>for rollback] --> B3[flyctl deploy --remote-only] --> FLY[Fly.io machine]
  F -- "frontend/**" --> W1[Node 22: npm ci,<br/>lint, test, build] --> W2[wrangler pages deploy dist<br/>project: 2doai-web] --> CFP[Cloudflare Pages]
  FLY -. Flyway migrates on boot .-> PG[(Neon PostgreSQL 18)]
  </pre>
  <h3>deploy-backend.yml</h3>
  <ul>
    <li>Tests gate the deploy: <code>mvn -B test</code> must pass first.</li>
    <li><code>concurrency: deploy-backend, cancel-in-progress: false</code> — deploys queue, never cancel mid-flight.</li>
    <li>Fly has no one-shot rollback, so the workflow logs the live image ref before deploying; roll back with <code>fly deploy --image &lt;recorded-image&gt;</code>. Safe because migrations are expand-only.</li>
  </ul>
  <h3>deploy-frontend.yml</h3>
  <ul>
    <li>Gate: <code>npm ci &amp;&amp; npm run lint &amp;&amp; npm test &amp;&amp; npm run build</code>.</li>
    <li><code>cancel-in-progress: true</code> — only the newest frontend deploy matters.</li>
    <li>Publishes <code>frontend/dist</code> via <code>cloudflare/wrangler-action</code>.</li>
  </ul>
  <h3>Secrets &amp; connected services</h3>
  <table>
    <tr><th>Service</th><th>Role</th><th>Config</th></tr>
    <tr><td>Fly.io</td><td>Backend host (single machine)</td><td><code>FLY_API_TOKEN</code> secret; DB creds via <code>SPRING_DATASOURCE_*</code> Fly secrets</td></tr>
    <tr><td>Cloudflare Pages</td><td>Frontend host + <code>/api/*</code> reverse proxy</td><td><code>CLOUDFLARE_API_TOKEN</code>, <code>CLOUDFLARE_ACCOUNT_ID</code></td></tr>
    <tr><td>Neon</td><td>Managed PostgreSQL 18 (EU/Frankfurt), pooled + SSL, autosuspend</td><td>consumed by backend only</td></tr>
    <tr><td>GitHub Actions</td><td>CI + CD runner</td><td><code>.github/workflows/</code></td></tr>
  </table>
  <p class="note">Operational detail (first-time setup, manual rollback, incident steps):
  <code>context/foundation/deployment-runbook.md</code>.</p>
</section>
```

- [ ] **Step 3: Verify section `cicd`** (1 diagram renders, `errors: 0`).

- [ ] **Step 4: Commit**

```bash
git add docs/index.html
git commit -m "docs(site): CI/CD pipeline and connected services"
```

---

### Task 8: Full verification pass + PR

**Files:**
- Modify: `docs/index.html` only if fixes are needed.

- [ ] **Step 1: Full sweep.** Open `docs/index.html` fresh (hard reload). Visit every hash — `#overview #architecture #backend #flows #glossary #cicd #roadmap` — and run the Global Constraints console check on each. Expected everywhere: `{shown: true, unrendered: 0, errors: 0}`.

- [ ] **Step 2: Link check.** Both data-model SVGs load (`naturalWidth > 0`); in-page anchors (`#flows`, `#architecture` from the roadmap section) navigate correctly.

- [ ] **Step 3: Content sanity.** Confirm every planned item carries `badge-planned` and nothing planned is presented as implemented.

- [ ] **Step 4: Fix anything found, commit fixes** (`docs(site): verification fixes`).

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin docs/dev-docs-site
gh pr create --title "docs: developer documentation SPA" --body "Single-file docs/index.html — architecture, per-slice class diagrams, sequence diagrams (implemented + planned), glossary, CI/CD. Per spec docs/superpowers/specs/2026-07-29-dev-docs-site-design.md"
```

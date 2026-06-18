# Deployment Runbook — how 2do AI went live

**Status:** live as of 2026-06-11
**Public origin:** https://2doai.app
**Architecture:** Pattern B (unified same-origin) — see [deployment.md](./deployment.md) for the *why*.
This file is the *how*: the exact sequence we ran to get the service online, plus the
incidents we hit and how we recovered.

```
                    2doai.app  (Cloudflare — single public origin)
                          │
            ┌─────────────┴──────────────┐
   /api/*  → Pages Function proxy → Fly backend  (https://2doai.fly.dev)
   /*      → Cloudflare Pages static build (Vite/React)
```

The version-controlled plumbing already lives in the repo:
- `backend/Dockerfile`, `backend/fly.toml`, `backend/.dockerignore`
- `backend/.../PingController.java` — `GET /api/v1/ping` smoke route (proves the proxy chain)
- `frontend/functions/api/[[path]].ts` — Pages Function that reverse-proxies `/api/*` to Fly
- `frontend/wrangler.toml` — `BACKEND_ORIGIN = https://2doai.fly.dev`
- `frontend/vite.config.ts` — dev proxy `/api → localhost:8080`
- `.github/workflows/deploy-backend.yml`, `.github/workflows/deploy-frontend.yml` — path-filtered auto-deploy on push to `master`

What follows is everything that could **not** be committed: the external accounts, CLI auth, and secrets.

---

## Phase 0 — Tooling & auth (once per machine)

```bash
# Fly CLI
brew install flyctl          # or: curl -L https://fly.io/install.sh | sh
fly auth login

# Cloudflare CLI
npm install -g wrangler      # or use npx wrangler ...
wrangler login
```

### What is Wrangler?

**Wrangler is Cloudflare's command-line tool** — the official CLI for deploying and managing
everything on the Cloudflare developer platform (Workers, Pages, and their storage/AI add-ons).
Think of it as the Cloudflare equivalent of what `fly` is for Fly.io: it authenticates to your
account and pushes code/config from your machine (or from CI) to Cloudflare's edge.

For this project we use it for one job — shipping the frontend:

- `wrangler login` — opens a browser to authenticate against your Cloudflare account (once).
- `wrangler pages deploy dist --project-name=2doai-web` — uploads the built static site **and**
  the `functions/` directory (our `/api/*` reverse proxy) to Cloudflare Pages.
- It reads `frontend/wrangler.toml` for project config (`name`, `pages_build_output_dir`,
  the `BACKEND_ORIGIN` var) so you don't pass those flags by hand.

You can run it without installing globally via `npx wrangler ...`. In CI we don't call it
directly — the `cloudflare/wrangler-action` GitHub Action wraps it (see `deploy-frontend.yml`).

---

## Phase 1 — Backend to Fly.io

Run from `backend/` (so `fly.toml` and `Dockerfile` are picked up).

```bash
cd backend
fly apps create 2doai        # name must match `app = "2doai"` in fly.toml
fly deploy                    # builds the Dockerfile remotely, boots a machine
```

Verify:

```bash
fly status
fly logs
curl https://2doai.fly.dev/actuator/health    # → {"status":"UP"}
curl https://2doai.fly.dev/api/v1/ping         # → {"status":"ok"}
```

> **Machine count gotcha.** Fly's HA default created **2 machines** on first deploy.
> For an after-hours MVP we only need one always-on machine (the scheduler-safe config
> keeps it from stopping). See *Incident 2* for how to safely get down to one.

---

## Phase 2 — Frontend to Cloudflare Pages

Build and deploy the static site. The first deploy creates the Pages project.

```bash
cd frontend
npm run build                                       # tsc + Vite → dist/
wrangler pages deploy dist --project-name=2doai-web
```

- When Wrangler asks for the **production branch**, enter `master` (matches our default branch and the CI workflow trigger).
- The `frontend/functions/` directory ships automatically with the Pages deploy — that's the `/api/*` reverse proxy.

Set the backend origin as a project variable (also declared in `wrangler.toml`, but set it on the project so the dashboard/CI agree):

```bash
wrangler pages project create 2doai-web   # only if not auto-created above
# BACKEND_ORIGIN = https://2doai.fly.dev   (Pages → Settings → Variables, or wrangler.toml [vars])
```

Verify the Pattern B chain through the Pages URL (before the custom domain exists):

```bash
curl https://2doai-web.pages.dev/api/v1/ping    # → {"status":"ok"}  (proxied to Fly)
```

---

## Phase 3 — Cloudflare API token (for CI)

Created in the Cloudflare dashboard → **My Profile → API Tokens → Create Token**:

- **Permissions:** `Account → Cloudflare Pages → Edit`
- **Account Resources:** Include → your account (or "All accounts" — same effect with one account)
- Copy the token once (shown only at creation) → it becomes the `CLOUDFLARE_API_TOKEN` GitHub secret.

Your **Account ID** is on the Cloudflare dashboard right sidebar → `CLOUDFLARE_ACCOUNT_ID`.

---

## Phase 4 — Fly deploy token (for CI)

```bash
fly tokens create deploy
```

> **Token format gotcha.** The value starts with `FlyV1 fm2_...`. Use the **entire string,
> including the `FlyV1 ` prefix and the space** — that whole value is the token (a Fly
> "macaroon"). Don't strip the prefix. This becomes the `FLY_API_TOKEN` GitHub secret.

---

## Phase 5 — GitHub secrets (wires up CI auto-deploy)

GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `FLY_API_TOKEN` | `FlyV1 fm2_...` (whole string from Phase 4) |
| `CLOUDFLARE_API_TOKEN` | token from Phase 3 |
| `CLOUDFLARE_ACCOUNT_ID` | account ID from Phase 3 |

After this, a push to `master` touching `backend/**` redeploys Fly, and `frontend/**`
redeploys Pages — independently (path-filtered workflows).

---

## Phase 6 — Custom domain `2doai.app`

The domain was registered (in Cloudflare) and connected to the Pages project:

1. Cloudflare Pages → **2doai-web → Custom domains → Set up a domain** → `2doai.app`.
2. Because the zone is in the **same Cloudflare account**, Cloudflare auto-creates the
   DNS record (CNAME flattening) and provisions the TLS certificate — no manual DNS.
3. `.app` is on the HSTS preload list, so the browser **forces HTTPS** automatically.

Verify end-to-end on the real origin:

```bash
curl https://2doai.app/api/v1/ping     # → {"status":"ok"}  (Cloudflare → Pages Function → Fly)
curl -I https://2doai.app/             # → 200, serves the SPA from the CDN
```

---

## Phase 7 — LLM provider (OpenRouter): key, privacy & live verification

Added for **F-02 (`ai-memory-integration`)**: the backend now reaches an LLM through the
`LlmClient` port (Spring AI's OpenAI client pointed at OpenRouter). This is everything that
could **not** be committed — the key, the Fly secret, the one-time dashboard privacy config,
and the live round-trip that turns the hermetic build into a live-verified one. The authoritative
decision is [`ai-provider.md`](./ai-provider.md); the steps below resolve its *"Do zweryfikowania
przy implementacji"* items **(a)**, **(b-on-Sonnet)** and **(d)**. Item **(c)** (Haiku Polish A/B)
and Haiku's `json_schema strict` support stay **deferred to S-09**, where auto-tag actually lands.

### 7.1 — Create the key (budget backstop)

OpenRouter dashboard → **Keys → Create Key**. Pre-pay a small credit balance and set a **low
per-key credit limit** — prepaid credits are a hard spend ceiling and the per-key cap is the
second budget backstop (`ai-provider.md` Rationale). Copy the key once (`sk-or-...`).

> **(a) credit-fee rate** — OpenRouter's revenue is a fee charged **when you top up credits**
> (not a per-token margin); Anthropic list prices pass through. Record the current top-up fee at
> provisioning: `____ %` (verify on the credits page).

### 7.2 — Privacy config (one-time, OpenRouter dashboard)

The PRD hard guardrail — *data and memory never train external models* — is enforced in **two
halves**:

- **In code (every request) — the load-bearing half:** the adapter sends
  `provider: { data_collection: "deny" }` on every call (`SpringAiLlmClient.NO_TRAINING_PROVIDER`),
  so OpenRouter routes **only** to providers that do not collect/train on the data, regardless of
  any account setting. Proven by the live test below. This alone satisfies the guardrail.
- **In the dashboard (account-wide) — belt-and-suspenders confirmation, not a hunt:** OpenRouter's
  defaults are **already safe** — it will **not** route to providers that train (or whose policy it
  can't confirm) **unless you switch the model-training toggle ON**. So this step is confirming you
  have *not opted in*, not flipping something off. Account menu → **Settings → Privacy** (the path
  is workspace-scoped now, e.g. `https://openrouter.ai/settings/privacy`, which may land you on
  `…/workspaces/default/settings`):
  - **Model training / "allow providers that may train on your data"** — separate **paid** and
    **free** model toggles → leave **OFF** (the default). Account-wide twin of the per-request
    `data_collection: deny`; the two merge.
  - **"OpenRouter Use of Inputs/Outputs"** (the 1% usage-discount opt-in that lets OpenRouter use
    your prompts/completions to improve the product) → **OFF** (the default).
  - If you don't see any training toggle switched on, that **is** the correct state — there's
    nothing to flip; the in-code `data_collection: deny` enforces it either way.

> **ZDR is deliberately NOT enabled.** The guardrail is about *training*, not *retention*;
> no-training satisfies it while keeping prompt caching. ZDR would cost caching and add
> price/latency variance via Bedrock/Vertex routing (`ai-provider.md` decision 3).

> **(d) no-training/logging — satisfied by code (verified 2026-06-18).** Enforcement is the
> per-request `data_collection: deny`, proven by the live Sonnet round-trip (`OpenRouterLiveTest`),
> backed by OpenRouter's default of not routing to training/unconfirmed providers and Anthropic's
> no-train-on-API commercial terms. The account-wide dashboard toggles were **not** separately
> dated — their defaults are no-training and the in-code routing holds regardless (see the
> two-halves note above). If a future operator wants the belt-and-suspenders dashboard glance,
> the navigation is in 7.2.

### 7.3 — Fly secret

```bash
cd backend
fly secrets set OPENROUTER_API_KEY=sk-or-...   # value never committed, never echoed in logs
fly secrets list                                # shows the name + a digest, never the value
```

`application.properties` binds it as `spring.ai.openai.api-key=${OPENROUTER_API_KEY:}` — the
empty default keeps boot and the hermetic suite green when the key is absent (the OpenAI client
runs key-less), so CI never needs the secret.

### 7.4 — Live verification (gated test)

`OpenRouterLiveTest` is **disabled unless `OPENROUTER_API_KEY` is set** (so CI stays hermetic).
Run it locally with the key injected via env — **never** a committed `.env` — to prove a real
round-trip on **Sonnet**, both free-text and `json_schema strict` structured:

```bash
cd backend
OPENROUTER_API_KEY=sk-or-... mvn test -Dtest=OpenRouterLiveTest   # Docker must be running (Testcontainers)
```

Both tests green confirms **(b-on-Sonnet)**: `json_schema strict` works for Sonnet through
OpenRouter. (Without the key the same command reports `Tests run: 2 … Skipped: 2`.)

### 7.5 — Deploy

Merge to `master` touching `backend/**` → the path-filtered `deploy-backend.yml` redeploys Fly
with the new config. No user-visible change; confirm the app boots with the secret resolvable:

```bash
curl https://2doai.fly.dev/actuator/health    # → {"status":"UP"}  (liveness stays UP)
curl https://2doai.app/api/v1/ping             # → {"status":"ok"}  (full chain still green)
```

---

## Verification checklist (the full chain)

| Check | Command | Expected |
| --- | --- | --- |
| Backend health (direct) | `curl https://2doai.fly.dev/actuator/health` | `{"status":"UP"}` |
| Backend smoke (direct) | `curl https://2doai.fly.dev/api/v1/ping` | `{"status":"ok"}` |
| Proxy via Pages URL | `curl https://2doai-web.pages.dev/api/v1/ping` | `{"status":"ok"}` |
| Proxy via custom domain | `curl https://2doai.app/api/v1/ping` | `{"status":"ok"}` |
| SPA served | `curl -I https://2doai.app/` | `200` |
| One machine, always-on | `fly status` | 1 machine, `started`, not auto-stopping |

---

## Incidents & recovery (what actually went wrong)

### Incident 1 — `/api/v1/ping` failed; direct Fly URL also down

**Symptom:** `curl https://2doai.app/api/v1/ping` failed and `curl https://2doai.fly.dev/api/v1/ping`
was also failing — consistently, not intermittently.

**Diagnosis:** isolating the curls showed the *backend itself* was down, not the proxy.
The Fly **app was Suspended** (both machines stopped, grey dots, health checks 0/1).
A Fly status-page ingress incident in **GRU** was ruled out — our app runs in `ams`, European
traffic enters via a European edge, and the symptom was steady rather than flapping.

**Fix:** started a machine manually → both URLs returned `200`.

```bash
fly status                       # find the machine id, see Suspended/stopped state
fly machine start <machine-id>
curl https://2doai.fly.dev/api/v1/ping   # → 200 once it boots
```

> The suspension root cause is billing/trust, **not** our config (the `fly.toml` is proven
> innocent). Until that's resolved the backend is "up but not yet durable." Follow-ups:
> confirm a valid payment method is on file, and set a Fly billing alert.

### Incident 2 — `fly scale count 1` destroyed the *running* machine

**Goal:** go from 2 machines (HA default) to 1.

**What happened:** `fly scale count 1` kept the **stopped** spare and destroyed the
**running** one → app returned `503`.

**Fix:** start the surviving machine.

```bash
fly machine start <surviving-machine-id>
# poll health until green:
curl https://2doai.fly.dev/api/v1/ping       # → 200
curl https://2doai.app/api/v1/ping           # → 200
```

**Lesson:** `fly scale count` doesn't let you choose *which* machine survives. Safer path to
one machine: `fly status` to list both, then `fly machine destroy <the-stopped-spare-id>`
(optionally `--force`) so you keep the one that's already serving traffic. End state: 1 machine,
`started`, checks passing — and because `auto_stop_machines = "off"`, Fly won't stop it on idle.

---

## Deferred / follow-ups

- **Fly durability:** resolve the suspension cause (billing/payment method), set a billing alert.
- **Preview URLs:** protect Pages `*.pages.dev` preview deploys with Cloudflare Access (free Zero Trust).
- **Database:** provision Neon (EU) and wire `SPRING_DATASOURCE_*` Fly secrets + JPA/Flyway when the first
  domain entity lands — not on the green-deploy critical path today (see [deployment.md](./deployment.md)).
- **Frontend hardening:** top-level error boundary + graceful `/api` 5xx handling before real API calls ship
  (under Pattern B a backend 5xx surfaces on the same origin as the app).
- **Rollback:** Fly has no one-shot rollback — the backend CI logs the current image before deploy;
  recover with `fly deploy --image registry.fly.io/2doai:<prev-tag>`.

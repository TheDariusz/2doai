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
- `backend/.../PingController.java` — `GET /api/ping` smoke route (proves the proxy chain)
- `frontend/functions/api/[[path]].ts` — Pages Function that reverse-proxies `/api/*` to Fly
- `frontend/wrangler.toml` — `BACKEND_ORIGIN = https://2doai.fly.dev`
- `frontend/vite.config.ts` — dev proxy `/api → localhost:8080`
- `.github/workflows/backend.yml`, `.github/workflows/frontend.yml` — per-side `quality` (every PR and push) + `deploy` (push to `master` only, `needs: quality`, path-filtered inside the job)
- `.github/workflows/repo-checks.yml` — repo-wide PR checks (docs link test, `fly.toml` `[[vm]] memory` assertion)

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
directly — the `cloudflare/wrangler-action` GitHub Action wraps it (see `frontend.yml`).

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
curl https://2doai.fly.dev/api/ping         # → {"status":"ok"}
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
curl https://2doai-web.pages.dev/api/ping    # → {"status":"ok"}  (proxied to Fly)
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
| `OPENROUTER_CI_KEY` | `sk-or-...` — **a second, separate OpenRouter key** for the AI code review (`ci-pipeline`, 2026-08-03) |

> **`OPENROUTER_CI_KEY` is deliberately not the app's key.** Give it its own low credit cap on the
> OpenRouter dashboard. The application key stays a **Fly secret** (`OPENROUTER_API_KEY`, Phase 7.3)
> and never enters GitHub, so a runaway CI loop can exhaust only the CI cap and never the production
> budget. Two consumers, two keys, two caps — see `ai-provider.md` → *Drugi konsument*.

> **`gh secret set NAME` needs a real TTY.** With no `--body`, gh prompts only when stdin is a
> terminal; otherwise it *reads stdin* — and an empty stdin sets the secret to an **empty string**,
> exits 0, and prints nothing. The secret then shows up in `gh secret list` with a fresh timestamp
> while behaving exactly like an unset one. This bit us on 2026-08-03 setting `OPENROUTER_CI_KEY`
> from a non-interactive shell. Set secrets from a real terminal or the web UI, never by piping.
> Symptom to recognise: `##[warning]OPENROUTER_CI_KEY is unset — review skipped` on a secret you
> just "set". Don't use `--body '<value>'` as the workaround — that puts the key in shell history.

Optionally set repo **variable** `AI_REVIEW_MODELS` (Settings → Secrets and variables → Actions →
**Variables**) to change the review model without a commit; the workflow carries a literal fallback,
so leaving it unset is fine. Model-swap rules: `ai-provider.md` → *Zmiana modelu bez commita*.

After this, a push to `master` touching `backend/**` redeploys Fly, and `frontend/**`
redeploys Pages — independently (path filtering now lives inside the jobs, not on the trigger).

### 5.1 — Required status checks on `master`

Every gate in `ci-pipeline` is decoration until `master` requires it. Settings → Branches → branch
protection for `master` → **Require status checks to pass before merging**, then select the checks
below using the exact names GitHub reports **after each workflow has run at least once**:

| Check | Workflow | Blocking? |
| --- | --- | --- |
| `backend quality` | `backend.yml` | yes |
| `frontend quality` | `frontend.yml` | yes |
| `checks` | `repo-checks.yml` | yes |
| `ai-review` | `ai-review.yml` | yes — but only its **security** step can fail; the advisory pass is `continue-on-error` |

> **Give every required job an explicit, unique `name:`.** A required status check is matched by
> check-run *name*, not by workflow — so two jobs both keyed `quality` (which is what `backend.yml`
> and `frontend.yml` shipped with) produce two contexts with one name, and a green frontend run can
> satisfy the rule while the backend one is red. Fixed 2026-08-03 by naming them `backend quality` /
> `frontend quality`. Renaming a required job's `name:` later **silently drops the requirement** —
> the old context stops reporting and the rule waits forever; update the rule in the same change.

> **Never add a trigger-level `paths:` / `paths-ignore:` to any of these four workflows.** A required
> check whose workflow is filtered out never reports, and the PR sits at *"Expected — waiting for
> status to be reported"* forever. All four filter by path **inside** the job precisely so they can
> be required. A human can force-merge past it; Dependabot cannot, so it would simply stall.

### 5.2 — GitHub security features: what this repo has

> **The repo went public on 2026-08-03, and that is what unlocked this section.** While it was
> private on a Free account, branch protection and rulesets both returned
> `403 Upgrade to GitHub Pro or make this repository public` — so every gate in `ci-pipeline` was
> unenforceable and 5.1 above was impossible. Going public was chosen over GitHub Pro. It was gated
> on a full-history secret audit (all 80 commits: no `.env` ever tracked, no token-shaped strings,
> single author email) — **run that audit again before ever flipping visibility on another repo.**
> Side effect: the paid tiers below stopped mattering, because these features are free on public
> repos.

| Feature | State (verified 2026-08-03) | Consequence |
| --- | --- | --- |
| **Code Scanning / SARIF upload** | **Available, not wired up** — free on public repos (was ~$30/committer/mo and org-scoped when this repo was private) | Trivy findings still go to `$GITHUB_STEP_SUMMARY` + artifacts. Routing them to the Security tab via `github/codeql-action/upload-sarif` is now possible and is **open follow-up work**, not done. |
| **GitHub secret scanning + push protection** | **Enabled** | No longer Trivy-only. Push protection blocks a known-pattern secret at push time — *preventive*, where `repo-checks.yml`'s Trivy pass is post-hoc. Keep both: Trivy catches patterns GitHub doesn't. |
| **Dependabot alerts + security updates** | **Enabled** — the "expected free, unverified" row is now confirmed | Surfaced **17 open alerts immediately** (npm only: undici, vite, postcss, react-router, brace-expansion). Security updates will open bump PRs for them automatically. |

> The distinction that matters: `.github/dependabot.yml` (added by `ci-pipeline`) drives **version**
> updates on a weekly schedule. Alert-driven **security** updates are a separate repository toggle
> and are the half that reacts within hours of an advisory rather than waiting for the next weekly
> run. Enabling the config file does **not** enable the toggle.

> **Public-repo consequence for CI:** pull requests can now arrive from forks. Fork PRs get no
> repository secrets and a read-only `GITHUB_TOKEN`, which is why `ai-review.yml` guards on
> `github.event.pull_request.head.repo.full_name == github.repository` — it skips rather than failing
> confusingly, and a job skipped by a job-level `if:` still reports green to the required check.

---

## Phase 6 — Custom domain `2doai.app`

The domain was registered (in Cloudflare) and connected to the Pages project:

1. Cloudflare Pages → **2doai-web → Custom domains → Set up a domain** → `2doai.app`.
2. Because the zone is in the **same Cloudflare account**, Cloudflare auto-creates the
   DNS record (CNAME flattening) and provisions the TLS certificate — no manual DNS.
3. `.app` is on the HSTS preload list, so the browser **forces HTTPS** automatically.

Verify end-to-end on the real origin:

```bash
curl https://2doai.app/api/ping     # → {"status":"ok"}  (Cloudflare → Pages Function → Fly)
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
runs key-less), so **the test suite never needs this secret** and `OPENROUTER_API_KEY` stays a Fly
secret only.

> That statement is scoped to the **test suite**, not to CI as a whole. Since `ci-pipeline`
> (2026-08-03) the repo has a *separate* OpenRouter consumer — the agentic code review in
> `.github/workflows/ai-review.yml` — which uses its own GitHub secret `OPENROUTER_CI_KEY` with its
> own credit cap (Phase 5 table). The app key above still never enters GitHub.

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

Merge to `master` touching `backend/**` → `backend.yml`'s `deploy` job redeploys Fly
with the new config. No user-visible change; confirm the app boots with the secret resolvable:

```bash
curl https://2doai.fly.dev/actuator/health    # → {"status":"UP"}  (liveness stays UP)
curl https://2doai.app/api/ping             # → {"status":"ok"}  (full chain still green)
```

---

## Phase 8 — Mail provider (Resend): sender domain, secrets & the first real email

Added for **S-05 (`natural-rhythm-return`)**: the natural-rhythm scheduler emails the proposal it
opened, which is the only way this app reaches a user who is, by definition, not looking at it.
Everything below is configuration — the code shipped with DEV-24 and needs no change.

**Two things fail silently if you skip them**, which is why they lead this phase: an unverified
sender domain means the provider accepts nothing (or accepts and never delivers), and a missing
`APP_BASE_URL` means every email links to `http://localhost:5173`. Neither raises an error the app
can see.

### 8.1 — Account and sender domain

**What Resend is.** A transactional-email provider: an SMTP relay with a dashboard, a delivery log
and the reputation work (bounce handling, DKIM signing) done for you. We reach it over plain SMTP
via `spring-boot-starter-mail`, so the provider is a *credentials* choice and not a dependency —
moving to Postmark or SES is four `spring.mail.*` lines, not a rewrite.

Budget ~10 minutes of clicking plus DNS propagation. Prerequisite: the `2doai.app` zone in the same
Cloudflare account as Phase 6.

1. **Create the account** — [resend.com/signup](https://resend.com/signup), email + password or
   GitHub/Google, no card. The free plan (3 000 messages/month, 100/day, 3 domains — checked
   2026-09-02) is orders of magnitude above this app's shape: one proposal per user every 2–7 days.
2. **Add the domain** — **Domains → Add Domain**, exactly `2doai.app`, EU region so the send leaves
   near the reader. It must be this domain: `application.properties` commits
   `app.mail.from=2do AI <propozycje@2doai.app>`, and a `From:` outside a verified domain is a
   message the provider drops without a bounce.
3. **Copy the DNS records into Cloudflare** — Resend shows three; add them under Cloudflare →
   `2doai.app` → **DNS → Records**. Copy the values from your dashboard verbatim; the ones below are
   shapes, not values:

   | Type  | Name                | Value (example)                                | What it is for |
   | ----- | ------------------- | ---------------------------------------------- | -------------- |
   | `MX`  | `send`              | `feedback-smtp.<region>.amazonses.com`, prio 10 | where bounces and complaints come back |
   | `TXT` | `send`              | `v=spf1 include:amazonses.com ~all`             | SPF — declares this relay may send as you |
   | `TXT` | `resend._domainkey` | `p=<long public key>`                           | DKIM — the key receivers check the signature against |

   They sit on a `send` subdomain rather than the apex, so none of it collides with mail you might
   later want to *receive* at `2doai.app`. `TXT` and `MX` have no orange cloud to get wrong; if the
   dashboard ever hands you a `CNAME`, set it to **DNS only** — Cloudflare rejects a proxied one with
   `Code: 1004`. DMARC is optional and not needed for verification.
4. **Verify** — press Verify and wait for **Verified**. Usually minutes, up to 72 h. The dashboard is
   the authority, not `dig`: what matters is what the provider's resolver sees.
5. **Create the API key** — **API Keys → Create API Key**, sending access, restricted to `2doai.app`.
   It starts `re_` and is shown **once**, so paste it straight into the `fly secrets set` of 8.2.
   There is no separate SMTP password: the username is the literal `resend` (already committed) and
   the API key *is* the password.

> **If verification stalls and you want to see an email tonight anyway:** Resend's shared
> `onboarding@resend.dev` sender needs no domain, but only delivers to the address you signed up
> with. Our `From:` is committed, so it takes an override on 8.3's command:
> `APP_MAIL_FROM=onboarding@resend.dev RESEND_API_KEY=… PROPOSAL_TEST_RECIPIENT=<your signup address> mvn test -Dtest=ResendLiveTest`.
> That proves the transport and the Polish copy — it does not prove the one thing 8.1 exists for, so
> it is a detour, not step 4.

### 8.2 — Fly secrets

```bash
cd backend
fly secrets set RESEND_API_KEY=re_...                 # value never committed, never echoed in logs
fly secrets set APP_BASE_URL=https://2doai.app        # what the email's link points at
fly secrets list                                      # names + digests, never values
```

`application.properties` binds them as `spring.mail.password=${RESEND_API_KEY:}` and
`app.mail.base-url=${APP_BASE_URL:http://localhost:5173}`. The empty default on the key is the same
trick Phase 7 uses: boot and the hermetic suite stay green without it, so **the test suite never
needs this secret**. It is not the whole story for hermeticism, though —
`ProposalSchedulerIntegrationTest` drives a real fire, so it mocks `EmailSender` outright rather than
relying on a key-less client to fail politely.

> `fly secrets set` restarts the machine. Setting both in one command
> (`fly secrets set RESEND_API_KEY=… APP_BASE_URL=…`) costs one restart instead of two.

### 8.3 — Live verification (gated test)

`ResendLiveTest` is **disabled unless both `RESEND_API_KEY` and `PROPOSAL_TEST_RECIPIENT` are set**,
so CI stays hermetic and nobody's inbox is a side effect of `mvn test`:

```bash
cd backend
RESEND_API_KEY=re_... PROPOSAL_TEST_RECIPIENT=you@example.com mvn test -Dtest=ResendLiveTest
```

It boots the whole app, so Docker must be running (Testcontainers) — the message it sends is built
by the same `ProposalEmail` the scheduler uses, from a template-arm proposal, so it costs no model
call.

It asserts almost nothing on purpose. What it proves cannot be asserted from inside the JVM, and the
real verification happens in the inbox:

- the message **arrives** — the sender domain is verified and the provider accepted it;
- the Polish reads like a friend rather than a notification;
- the link opens the app (and points at `2doai.app`, not localhost — that is `APP_BASE_URL` proving
  itself).

Without the variables the same command reports `Tests run: 1 … Skipped: 1`.

### 8.4 — Deploy and prod smoke

Merge to `master` touching `backend/**` → `backend.yml` redeploys Fly. The scheduler announces itself
on boot; that line is the cheapest confirmation it is wired:

```bash
fly logs | grep "Natural rhythm loaded"     # → "Natural rhythm loaded: N account(s) scheduled"
curl https://2doai.fly.dev/actuator/health  # → UP, and the proposalScheduler indicator with it
```

`0 account(s)` means the table is empty — the rhythm has nobody to return to, so register on
https://2doai.app before anything below.

To force one cycle rather than waiting days for it, move the account's moment into the past **and
restart the machine**:

```sql
-- 1. Neon SQL editor. Expect "UPDATE 1"; UPDATE 0 means you typed a different address.
update app_user set next_proposal_at = now() - interval '1 minute' where email = 'you@example.com';
```

```bash
# 2. Restart, so the boot re-reads the column into the map the tick actually consults.
fly machine restart <id>          # or: fly apps restart 2doai
fly logs | grep -E "Natural rhythm loaded|Delivered"
```

**The restart is not optional, and the reason is the design.** `ProposalScheduler.fireDue` reads an
in-memory map "and only the map" — that is what lets a tick run every 60 s without waking Neon.
`loadSchedule()` fills that map exactly once, on `ApplicationReadyEvent`. So between boots the
`next_proposal_at` column is the *durability record*, not the live authority: an UPDATE against it
changes nothing any tick will read. The boot is what promotes the column back to being the schedule.

Two more things that make a forced fire look like a silent failure, both correct behaviour:

- **Nothing neglected, nothing said.** `fire()` sends only if `proposeScheduled` returns a proposal,
  and logs nothing when it doesn't. The account needs a genuinely overdue entry — easiest is a `TASK`
  with a `due_date` a few days past.
- **Outside 9:00–21:00 Warsaw the tick returns immediately**, before it looks at a single account. A
  restart at 21:05 produces no fire and no log line until 9:00 the next morning.

Expect, within a minute of the restart: `Natural rhythm loaded: 1 account(s) scheduled`, then
`Delivered: a NN-char subject to a @… address`, the email itself, and the same proposal already on
`https://2doai.app/goals` without pressing anything.

> **The proposal survives a mail failure.** It is stored before the message is attempted and the send
> sits inside the fire's `catch`, so a provider outage costs the nudge, not the proposal — the card is
> waiting in the app either way and the rhythm still reschedules. If email looks broken, check
> `/goals` before assuming the cycle did not run.

---

## Verification checklist (the full chain)

| Check | Command | Expected |
| --- | --- | --- |
| Backend health (direct) | `curl https://2doai.fly.dev/actuator/health` | `{"status":"UP"}` |
| Backend smoke (direct) | `curl https://2doai.fly.dev/api/ping` | `{"status":"ok"}` |
| Proxy via Pages URL | `curl https://2doai-web.pages.dev/api/ping` | `{"status":"ok"}` |
| Proxy via custom domain | `curl https://2doai.app/api/ping` | `{"status":"ok"}` |
| SPA served | `curl -I https://2doai.app/` | `200` |
| One machine, always-on | `fly status` | 1 machine, `started`, not auto-stopping |
| Rhythm loaded at boot | `fly logs \| grep "Natural rhythm loaded"` | `Natural rhythm loaded: N account(s) scheduled` |
| Scheduler alive | `curl https://2doai.fly.dev/actuator/health` | `UP` — the `proposalScheduler` indicator reports the tick's own pulse |
| Sender domain verified | Resend dashboard → Domains | `2doai.app` **Verified** (an unverified domain drops mail silently) |
| Mail secrets present | `fly secrets list` | `RESEND_API_KEY` and `APP_BASE_URL` both listed |

---

## Incidents & recovery (what actually went wrong)

### Incident 1 — `/api/ping` failed; direct Fly URL also down

**Symptom:** `curl https://2doai.app/api/ping` failed and `curl https://2doai.fly.dev/api/ping`
was also failing — consistently, not intermittently.

**Diagnosis:** isolating the curls showed the *backend itself* was down, not the proxy.
The Fly **app was Suspended** (both machines stopped, grey dots, health checks 0/1).
A Fly status-page ingress incident in **GRU** was ruled out — our app runs in `ams`, European
traffic enters via a European edge, and the symptom was steady rather than flapping.

**Fix:** started a machine manually → both URLs returned `200`.

```bash
fly status                       # find the machine id, see Suspended/stopped state
fly machine start <machine-id>
curl https://2doai.fly.dev/api/ping   # → 200 once it boots
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
curl https://2doai.fly.dev/api/ping       # → 200
curl https://2doai.app/api/ping           # → 200
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

---
project: 2do AI
researched_at: 2026-06-03
recommended_platform: Fly.io (backend) + Cloudflare Pages (frontend)
runner_up: Render
context_type: mvp
tech_stack:
  language: Java 25 (+ TypeScript/React 19 frontend)
  framework: Spring Boot 4.x (+ Vite PWA)
  runtime: JVM (container) / static assets
---

## Recommendation

**Deploy the backend on Fly.io and the frontend on Cloudflare Pages, under a single same-origin domain (Pattern B — Cloudflare reverse-proxies `/api/*` to Fly).**

This validates the prior `deployment.md` decision after stress-testing it against Railway, Render, and AWS. Fly.io is the cheapest viable host for an always-on JVM scheduler (~€3–6/mo for a 512 MB EU machine vs ~€7–45/mo for the alternatives), gives full CLI parity (`flyctl` does deploy/logs/secrets/scale/rollback), and is the platform the existing reverse-proxy architecture is already designed around. The hard filter is the FR-011 background scheduler (`has_background_jobs: true`, `has_realtime: false`): it needs a persistent JVM process, which eliminates every serverless-JS host (Cloudflare Workers, Vercel, Netlify) as a *backend*. The interview confirmed DX-priority, no platform lock-in from familiarity, single-region EU, and external managed Postgres (Neon/Supabase) being acceptable — all of which favour Fly. Render is a close, more cost-predictable runner-up; AWS is a heavier fallback only with a hard reason to stay in-ecosystem.

## Platform Comparison

Hard filter applied first: the backend is a **JVM Spring Boot app with an always-on scheduler**, so serverless-JS platforms (Cloudflare Workers, Vercel, Netlify) are dropped as *backend* hosts — they cannot run a persistent JVM process. The contest is three container PaaS, with AWS added at the user's request. The frontend host is settled: **Cloudflare Pages** (also the reverse-proxy layer).

| Platform | CLI-first | Managed/serverless | Agent-readable docs | Stable deploy API | MCP / integration | Verdict |
|---|---|---|---|---|---|---|
| **Fly.io** | Pass — `flyctl` full loop | Pass — managed VMs, auto TLS/health | Partial — searchable docs + MCP, no `llms.txt` | Pass — `fly deploy`; rollback = redeploy prior image tag | Partial — MCP server **beta** | **Recommended** |
| **Render** | Partial — real CLI (stable), but **rollback is dashboard-only** | Pass — + first-class Cron Job / Background Worker types | Pass — `llms.txt` + `llms-full.txt` + Claude skills | Pass — `render deploy --wait`, JSON output | Pass — MCP server GA (read-only) | **Runner-up** |
| **Railway** | Pass — full headless CLI + project tokens | Pass — managed | Partial — good docs, MCP, no `llms.txt` | Pass — `railway up`, `railway redeploy` | Pass — MCP server GA (remote OAuth) | Third |
| **AWS (Lightsail, best tier)** | Partial — `aws lightsail` works; weak rollback | Partial — shared-CPU fixed VMs; the truly-managed tier (App Runner) is **sunset** | Partial — vast docs, no `llms.txt`; ECS MCP **preview** | Partial — scriptable but verbose; weak rollback | Partial — ECS MCP **preview**, region-unclear | Rejected for MVP |

Soft weights from the interview — DX-priority, no familiarity tie-break, single-region EU, external Postgres OK — pull toward the cheapest full-CLI PaaS that runs an always-on JVM cleanly. Cost reality for an unfunded after-hours MVP was the deciding lens: Fly ~€3–6/mo, Render ~€7/mo (always-on Starter; in-process `@Scheduled` works, no spin-down), Railway ~€10–30/mo (usage-based, no scale-to-zero for an always-on worker burns the $5 credit fast), AWS Lightsail ~€32/mo with more setup. All four support an EU region (Fly `ams`, Render Frankfurt, Railway Amsterdam, AWS `eu-central-1`).

### Shortlisted Platforms

#### 1. Fly.io (Recommended — backend)

Cheapest always-on JVM host with a complete CLI loop and a native fit for the existing Cloudflare→Fly reverse-proxy. `fly deploy` is deterministic; `fly logs`, `fly secrets`, `fly scale` cover the agent ops loop. JVM runs fine on a 512 MB EU machine with documented memory discipline. It wins on cost and on already being the architecture's assumption.

#### 2. Render

The most cost-predictable option (~€7/mo always-on Starter, no spin-down, so the in-process scheduler just works) and the **best agent documentation** (`llms.txt` + `llms-full.txt` + Claude Code skills), plus first-class Cron Job / Background Worker service types and an EU Frankfurt region. The single gap vs Fly: **rollback is dashboard-only**, an agent-ops ding, and it's slightly pricier. The obvious swap target if Fly's config-discipline tax proves annoying.

#### 3. Railway

Smoothest raw DX (Railpack auto-detects Maven; full headless CLI; GA MCP server). Held back by cost predictability: usage-based billing with no scale-to-zero for an always-on JVM worker drains the $5 Hobby credit quickly (~€10–30/mo realistic), and Railpack defaults to **Java 21** — Java 25 needs an explicit `RAILPACK_JDK_VERSION` override (or a Dockerfile).

#### Rejected: AWS

Evaluated at the user's request. The one tier that matched the shape — **App Runner** (fully-managed containers) — is **closed to new customers as of 2026-04-30, full EOL 2026-06-12**, and CPU-throttles when no HTTP request is in flight, which would silently stall the FR-011 scheduler regardless. **Copilot CLI EOLs the same day.** The successor (ECS Express Mode) is six months old. The viable fallback is **Lightsail Containers** (~€10/mo fixed + RDS ~€22/mo), but it scores behind all three PaaS on agent-friendliness and carries the structural solo-dev taxes: bill-shock from NAT (~€33/mo) / ALB (~€16/mo) / idle RDS on the heavier tiers, an overwhelming console, and platform churn. Recommended only with a hard reason to stay inside AWS (existing org, compliance, credits).

## Anti-Bias Cross-Check: Fly.io

### Devil's Advocate — Weaknesses

1. **JVM OOM on the cheap tier.** Spring Boot 4 + Hibernate + an AI client on 256 MB gets OOM-killed; 512 MB is the floor and may be tight under real load (budget for 1 GB / ~€5.70/mo).
2. **Memory silently reverts.** If `[[vm]] memory` isn't pinned in `fly.toml`, a redeploy can drop back to 256 MB even after scaling up via CLI — config drift → crash loop.
3. **No first-class rollback.** Rollback = `fly deploy --image <previous-tag>`; the agent must record the prior image digest before each deploy or recovery is fumbly.
4. **Scale-to-zero kills the scheduler.** The single most dangerous misconfig *for this app*: if `auto_stop_machines` isn't off / `min_machines_running ≥ 1`, the machine stops when idle and FR-011 (the product's whole differentiator) silently never fires.
5. **Unmanaged-Postgres trap + single-region capacity.** Fly's built-in Postgres is unmanaged (you own backups/failover); a single machine pinned to `ams` has no auto-failover if regional capacity is exhausted at deploy.

### Pre-Mortem — How This Could Fail

The backend shipped to a single 512 MB Fly machine and worked in week one. The first failure was silent: a cost-saving instinct left `auto_stop_machines` at its default and `min_machines_running` at 0, so overnight the machine stopped and the natural-rhythm scheduler never fired — unnoticed for two weeks because the symptom is "the app does nothing," not an error. Meanwhile the AI auto-tagging client and Hibernate pushed live memory past 512 MB; the JVM was OOM-killed intermittently, surfacing as random 502s on `/api/*` — which, under Pattern B, looked to users like the whole app was broken, not a down API. To "get a DB fast," Postgres was created as an unmanaged Fly app with no backup schedule; in month four a volume issue corrupted it with no recent snapshot, violating the PRD's hard durability guardrail and losing the AI memory — the most intimate, irreplaceable data. Root cause: treating Fly's cheap scale-to-zero defaults as safe defaults for an always-on, stateful, scheduler-driven app.

### Unknown Unknowns

- Fly's defaults optimise for scale-to-zero web apps — the *opposite* of an always-on scheduler. The correct config (`auto_stop_machines = "off"`, `min_machines_running = 1`, pinned memory) is the non-default and must be explicit in `fly.toml`.
- `fly deploy` memory can silently revert to 256 MB if `[[vm]] memory` isn't in `fly.toml`, even after a CLI `fly scale memory` — config drift between CLI scaling and the manifest.
- There is no real "rollback" — you must keep release image tags yourself; the agent ops loop should record the prior image digest before each deploy.
- **Pattern B coupling:** a Fly outage or cold-start shows up as `/api/*` 5xx on the *same origin* as the app — users see a broken app, not a down API. The frontend needs graceful API-error handling from day one.
- No permanent free tier anymore (2 VM-hours / 7-day trial, then card required) — budget ~€3–6/mo from day one.
- Single-region `ams` has no automatic failover — acceptable for an MVP, but a conscious, logged acceptance.

## Operational Story

- **Preview deploys**: Frontend — Cloudflare Pages auto-creates a per-branch/PR preview URL on push (atomic, aliased); protect them behind **Cloudflare Access** (free Zero Trust, ≤50 users) for a private MVP. Backend — Fly has no built-in PR previews; use a throwaway app (`fly apps create 2doai-pr-NNN` + `fly deploy -a …`) or a staging app for manual verification.
- **Secrets**: Backend — `fly secrets set KEY=value` (encrypted at rest, injected at boot; `fly secrets list` shows digests only). DB URL for external Neon/Supabase lives here. CI tokens — GitHub Actions secrets (`FLY_API_TOKEN`, `CLOUDFLARE_API_TOKEN`). Frontend build-time vars — Cloudflare Pages project env vars. Rotation is CLI-driven; never commit `.env`.
- **Rollback**: Backend — `fly releases` to list, then `fly deploy --image registry.fly.io/2doai:<prev-tag>` (record the prior image digest before each deploy — there is no one-shot rollback). Frontend — `wrangler pages deployment list` then redeploy/promote a prior deployment. **DB-migration caveat:** schema changes don't roll back with the image — gate migrations (Flyway/Liquibase) to be backward-compatible.
- **Approval**: Agent may do unattended: `fly deploy`, `fly logs`, scale within budget, read secrets' digests, Pages preview deploys. Requires a human: promoting frontend to the production custom domain, rotating the primary DB credential, any destructive op (`fly apps destroy`, dropping the database), and a paid-tier/scale change that meaningfully raises cost.
- **Logs**: Backend — `fly logs -a 2doai` (live tail), `fly logs --no-tail` (recent), `fly status` for machine health; optionally the Fly MCP server (beta). Frontend — `wrangler pages deployment tail`; build logs in the Pages dashboard / CI output. CI — GitHub Actions run logs via `gh run view`.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| Machine scale-to-zero stops the FR-011 scheduler silently | Pre-mortem / Unknown unknowns | M | H | In `fly.toml`: `auto_stop_machines = "off"` + `[http_service] min_machines_running = 1`; add a startup log line + a liveness check that the scheduler thread is alive |
| JVM OOM-killed on 512 MB under load | Devil's advocate | M | H | Pin `[[vm]] memory = 512` (start 1024 if AI client is heavy); set `-XX:MaxRAMPercentage=70`; alert on restarts |
| `fly.toml` memory silently reverts to 256 MB on redeploy | Unknown unknowns | M | H | Always declare `[[vm]] memory` in `fly.toml`; never rely solely on `fly scale memory`; assert in CI |
| Unmanaged Fly Postgres → data loss (violates durability guardrail) | Devil's advocate / Pre-mortem | M | H | Use **external managed Postgres (Neon or Supabase)** with automated backups + PITR; never use Fly's unmanaged Postgres for MVP data |
| Rollback fumbled — no prior image tracked | Devil's advocate / Unknown unknowns | M | M | CI records the prior image digest before each deploy; document the `fly deploy --image <tag>` rollback runbook |
| Pattern B: Fly outage/cold-start surfaces as broken app on same origin | Unknown unknowns | M | M | Frontend handles `/api/*` 5xx/timeout gracefully (retry + friendly state); keep `min_machines_running = 1` to avoid cold starts |
| DB migration not roll-back-safe when image is reverted | Research finding | M | M | Backward-compatible migrations (expand/contract); decouple deploy from migration |
| Single-region `ams` has no failover | Devil's advocate | L | M | Accept consciously for MVP; document; revisit at scale (out of MVP scope) |
| No permanent free tier — small recurring cost from day one | Research finding | H | L | Budget ~€3–6/mo Fly + Neon free/idle tier; add a billing alert |
| Cloudflare steering Pages → Workers Static Assets | Research finding | L | L | Pages is GA and not deprecated; Cloudflare commits to transparent migration. Stay on Pages for MVP; note Workers Static Assets as the forward path if starting fresh later |
| Java 25 is very new — base-image/library lag | Research finding | L | M | Use `eclipse-temurin:25-jre`; verify Spring Boot 4.x + dependency support; pin versions |

## Getting Started

Versions assumed: Spring Boot 4.x / Java 25 / Maven (backend), React 19 + Vite (frontend). Commands validated against current `flyctl` and `wrangler` behaviour, not generic marketing docs.

1. **Backend container** — add a multi-stage `Dockerfile` to `backend/`: build stage `maven:3.9-eclipse-temurin-25` running `mvn -q package -DskipTests`, runtime stage `eclipse-temurin:25-jre` running the fat JAR. (Fly uses the Dockerfile builder by default — no Spring buildpack needed.)
2. **Provision Fly** — from `backend/`: `fly launch --no-deploy --region ams` (generates `fly.toml`; pick app name e.g. `2doai`). Then edit `fly.toml` to set `[[vm]] memory = 512`, `auto_stop_machines = "off"`, and `[http_service] min_machines_running = 1` **before first deploy** (these are the non-default, scheduler-safe settings).
3. **External Postgres + secrets** — create a Neon (or Supabase) project in an EU region; then `fly secrets set SPRING_DATASOURCE_URL=… SPRING_DATASOURCE_USERNAME=… SPRING_DATASOURCE_PASSWORD=…` and any AI provider key. Deploy: `fly deploy`. Verify: `fly status` + `fly logs`.
4. **Frontend on Cloudflare Pages** — connect the repo (Pages → Git) or `npx wrangler pages deploy dist` after `npm run build` in `frontend/`. Configure build: command `npm run build`, output `dist`. For local dev, keep the Vite dev proxy pointing `/api` → `http://localhost:8080`.
5. **Wire Pattern B reverse proxy** — add a Cloudflare Pages Function (or a small Worker) that forwards `/api/*` to the Fly backend's hostname, giving one same-origin domain (`2doai.app`) with no CORS. Add the custom domain + TLS in Cloudflare; protect preview URLs with Cloudflare Access.

## Out of Scope

The following were not evaluated in this research:
- Docker image configuration (only the deploy path was confirmed)
- CI/CD pipeline setup (path-filtered GitHub Actions are noted in `deployment.md`)
- Production-scale architecture (multi-region, HA/failover, DR)

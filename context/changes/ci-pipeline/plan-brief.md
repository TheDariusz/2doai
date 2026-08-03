# CI Pipeline — Plan Brief

> Full plan: `context/changes/ci-pipeline/plan.md`
> Research: `context/changes/ci-pipeline/research.md`

## What & Why

Today the only place tests run is inside the two `push: master` deploy workflows — so a broken PR is
discovered *after* it merges and starts deploying. This change adds a pull-request quality gate,
Trivy dependency/secret scanning, and a model-agnostic agentic code review routed through OpenRouter
that can block a merge on a high-confidence security finding.

## Starting Point

Two path-filtered workflows (`deploy-backend.yml`, `deploy-frontend.yml`), both `push: master` only.
The backend one runs `mvn -B test` then records the live Fly image (the *only* rollback mechanism
Fly offers) and deploys; the frontend one runs `npm ci`/lint/test/build then deploys to Pages. No PR
triggers, no scanning, no static analysis on the backend at all. `/check` already defines what
"green" means locally — CI's job is to run it, not reinvent it.

## Desired End State

Every PR touching `backend/**` or `frontend/**` runs that side's full gate, and neither Fly nor
Pages can deploy unless that gate passed. Dependency and secret findings land in the run summary,
with fixable HIGH/CRITICALs failing the build. Every PR gets an advisory AI review comment, and a PR
carrying a high-confidence high-severity security finding shows a red check that branch protection
refuses to merge.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| CI ↔ CD shape | One workflow per side; `deploy` job `needs:` `quality` | Preserves the load-bearing path-filtered independent-deploy architecture | Research |
| Path filtering | Moved from the trigger into the job | A required check that never runs blocks a PR forever — trigger-level `paths:` and required checks are incompatible | Plan |
| Auto-merge | Patch + minor, npm and maven only | Actions bumps change what CI executes, so accepting one unreviewed undercuts the SHA-pinning it just landed | Plan |
| Trivy scope | Deps + secrets; no image, no jar `rootfs` | `--remote-only` means no local image exists to scan; jar scanning would need an extra `mvn package` | Plan |
| Trivy placement | Backend scan **inside** the quality job, after `mvn` | Cold `~/.m2` makes Trivy hard-FATAL on HTTP 429 — reproduced empirically | Research |
| Findings destination | Step summary + artifact, not the Security tab | Private user-owned repo cannot buy Code Scanning at any price | Research |
| AI review build-vs-buy | DIY `diff → OpenRouter → json_schema → gate` | Off-the-shelf actions are either Anthropic-only or hand an agent a shell plus your API key | Research |
| AI passes | Advisory (Sonnet 5) + blocking security (Opus 5) | Separating "here's a thought" from "must not merge" is what keeps these gates switched on | Plan |
| Gate predicate | `security && high && confidence ≥ 0.8` | Two independent keys — severity alone is trivially inflated by a model | Plan |
| Failure mode | Fail **open** on 429/5xx/parse errors | A provider outage must not block merges at 11 pm | Research |
| Model list | `AI_MODELS` env literal, overridable by a repo variable | Repo variables aren't passed to fork PRs and resolve to empty strings, so the literal keeps the job valid | Research |
| OpenRouter key | Separate credit-capped `OPENROUTER_CI_KEY` | A runaway CI loop can only exhaust the CI cap, never the app's budget | Plan |
| Pages-proxy typecheck | Separate `tsconfig.functions.json` project | Verified: extending `tsconfig.app.json` breaks `src/api/client.ts` via Workers/DOM global clash | Plan |
| Change id | Keep `ci-pipeline`, retarget the roadmap row | The scope genuinely outgrew the roadmap's `pr-branch-ci` framing | Plan |
| Dependabot | Actions + npm + maven, grouped, weekly | SHA-pinned actions are immutable and never self-update — Dependabot is what keeps pinning from becoming staleness | Plan |

## Scope

**In scope:** PR triggers on both sides · deploy gated behind quality · Trivy deps (backend + frontend)
· repo-wide secret scan · DIY OpenRouter review (advisory + blocking) · grouped weekly Dependabot
across three ecosystems · `tsconfig.functions.json` · wiring `docs/index.test.mjs` · the
`[[vm]] memory` assertion `infrastructure.md:87` promised · runbook/roadmap/`/check` reconciliation ·
branch protection

**Out of scope:** container image scanning · `trivy rootfs` on the fat jar · SARIF / Security tab ·
Java linters or static analysis · `pull_request_target` · AI review of PR title/body/comments ·
bot `REQUEST_CHANGES` · any CI database connection or Flyway run

## Architecture / Approach

```
PR ──┬─► backend.yml    : quality (mvn test → trivy fs)          ──┐
     ├─► frontend.yml   : quality (ci→lint→test→build → trivy fs) ──┤ required checks —
     ├─► repo-checks.yml: secret scan + docs test + fly.toml grep ──┤ all always report
     └─► ai-review.yml  : diff → OpenRouter ─┬─ advisory (no block)─┘
                                             └─ security (may exit 1)

push master ─► same quality jobs ─► deploy job (needs: quality, if: push && side changed)

dependabot (weekly, grouped) ─► same checks ─► auto-merge if patch/minor && npm|maven
```

The AI review is repo-wide and PR-only, so it gets its own workflow. Trivy *must* be per-side for
the backend (Maven cache coupling); the secret scan is footloose. Every workflow carrying a required
check runs unconditionally and decides internally whether its expensive steps apply — path filtering
lives in the jobs, not the triggers, because a check that never reports blocks the PR rather than
skipping it.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. PR quality gate | Tests/lint/build run on PRs; deploys gated; Pages proxy typechecked | Two things must move from workflow level into the jobs — concurrency (or PR runs contend with deploys) and path filtering (or required checks hang) |
| 2. Trivy + Dependabot | Dependency + secret scanning with an informational and a gate pass; grouped weekly bump PRs | Backend scan 429s and hard-fails if it isn't ordered after `mvn`; frontend gate lands red on react-router unless bumped or suppressed |
| 3. AI review | Advisory + blocking OpenRouter passes with a strict JSON-schema gate | False positives blocking merges; recurring cost roughly doubles the €3–6/mo infra budget |
| 4. Enforcement + docs | Branch protection, Dependabot alerts + auto-merge, secret registry, roadmap/runbook reconciliation | Nothing in the repo can assert branch protection was actually configured; auto-merge is only safe *after* it exists |

**Prerequisites:** a Linear issue (labels `infrastructure` + `Maintenance`) with its `branchName`
checked out · a new credit-capped OpenRouter key before Phase 3 · repo admin access for Phase 4

**Estimated effort:** ~3–4 sessions; Phase 1 is the bulk, Phase 4 is mostly documentation plus one
settings screen

## Open Risks & Assumptions

- **The `[[vm]] memory` grep is a new assertion** — if `backend/fly.toml` doesn't currently satisfy it,
  Phase 1 lands red until the declaration is added.
- **The frontend Trivy gate is red today** (react-router `GHSA-qwww-vcr4-c8h2`, fixed in 8.3.0);
  Phase 2 must either bump it or record a time-boxed suppression.
- **`models[]` fallback × `json_schema` is undocumented upstream** — every candidate model must be
  verified against `?supported_parameters=structured_outputs` before being added to the list.
- **Branch protection state is unverified** (the `gh` wrapper is broken in this shell), so Phase 4's
  first step is discovery, not configuration.
- **Renaming the workflow files changes check names** — PRs open across the rename will need a
  rebase before branch protection stops blocking them on checks that no longer exist.
- **AI review cost is assumed ~$0.11–$0.27/PR at ~30 PRs/month.** If PR volume or diff size runs
  higher, the per-key cap is the backstop, not the estimate. Dependabot PRs are guarded out of the
  review, so weekly bumps don't inflate this — but that guard is load-bearing, because Dependabot
  runs don't receive repository secrets and would otherwise fail rather than skip.
- **Dependabot's availability on this account is unverified.** It's expected to be free on private
  repos, unlike Code Scanning — but the research only established what this plan tier *cannot* buy,
  and this wasn't among the things checked.
- **Auto-merge may not trigger a deploy.** `GITHUB_TOKEN`-performed actions don't spawn new workflow
  runs, and deploys are `push: master`-triggered — so an auto-merged bump could land on `master`
  without deploying, diverging it from production. Phase 4 verifies this with one throwaway PR
  before the behavior is trusted; three mitigations are named.
- **Grouped PRs blunt the non-major filter.** A grouped bump PR can carry patch, minor and major
  together; the filter relies on `fetch-metadata` reporting the highest semver bump across the
  group, which is expected but unconfirmed. The silent failure mode is auto-merging a major.

## Success Criteria (Summary)

- A PR that breaks a backend test, breaks frontend lint, or adds a plausible SQL-injection sink
  cannot be merged.
- A deploy to Fly or Pages is impossible unless that side's quality gate passed on the merge commit.
- Dependency, secret, and AI findings are visible in the run summary without opening raw logs — and
  an OpenRouter outage never blocks a merge.

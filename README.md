# 2do AI

2do AI is a personal planning application for current tasks, long-term goals, and dreams, with an
AI partner that returns proactively in a natural rhythm.

**Live:** <https://2doai.app>

- [Architecture and developer documentation](docs/index.html)
- [Product requirements](context/foundation/prd.md)
- [Roadmap](context/foundation/roadmap.md)
- [Test plan](context/foundation/test-plan.md) — named risks and the suites that defend them
- [Deployment runbook](context/foundation/deployment-runbook.md)

The repository contains two independent projects. There is no root workspace or shared build.

## What it does

- **Three layers in one view.** A current task, a long-term goal and a dream are one aggregate under
  three sets of rules (`TASK` / `GOAL` / `DREAM`), listed on a single screen and filterable by layer
  and by category. Each entry can be filed under one of 11 life domains.
- **The AI comes back on its own.** Each account's next moment is drawn 2–7 days out, at an hour
  between 9:00 and 21:00. When it arrives, a deterministic engine picks the most neglected entry —
  weighted so one category cannot dominate the nudges — a model phrases it, and it is delivered both
  as an email and as a card already waiting in the app. Nothing to press, and no daily digest.
- **Four ways to answer.** `STARTING`, which is followed by a concrete first step; `NOT_NOW`;
  `REMIND_LATER` with a term the user names; or `NEVER`. At most one proposal is ever pending — an
  unanswered one is superseded by the next rather than piling up.
- **Accounts** with email and password, server-side sessions, and every row scoped to its owner.

Not built yet: seeding the AI memory profile, AI category auto-tagging, priority categories, and
offline read-only. Status per slice lives in the [roadmap](context/foundation/roadmap.md).

## Prerequisites

- JDK 25
- Docker Desktop (for local PostgreSQL 18 and backend integration tests)
- Node.js 22 with npm

## Run locally

Start the backend from its own directory:

```sh
cd backend
./mvnw spring-boot:run
```

Spring Boot starts the PostgreSQL service declared in `backend/compose.yaml`, waits for it to become
healthy, applies Flyway migrations, and serves the API on `http://localhost:8080`.

In a second terminal, start the frontend:

```sh
cd frontend
npm ci
npm run dev
```

Vite serves the application on `http://localhost:5173` and proxies `/api` to the backend, mirroring
the same-origin setup production gets from Cloudflare.

Two integrations are optional locally, each behind one environment variable the backend reads at
startup. Boot and the whole hermetic test suite stay green without either:

- `OPENROUTER_API_KEY` — the model that phrases a proposal. Without it the call fails and a built-in
  template phrases it instead, so the loop still runs end to end.
- `RESEND_API_KEY` — SMTP delivery of the rhythm email. Without it the proposal still appears in the
  app; no message is sent.

Inject them into the backend process; never put a real key in a committed file.

## Verify changes

Backend:

```sh
cd backend
./mvnw test
./mvnw package
```

Frontend:

```sh
cd frontend
npm test
npm run lint
npm run build
```

Architecture documentation:

```sh
node --test docs/index.test.mjs
```

Before adding a test, read [the test plan](context/foundation/test-plan.md): it names the risks this
project protects against, maps each one to the suite that already defends it, and records what is
deliberately left untested.

Before committing, run the repository `/check` skill for the complete backend and frontend quality
gate.

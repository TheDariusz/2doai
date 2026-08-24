# 2do AI

2do AI is a personal planning application for current tasks, long-term goals, and dreams, with an
AI partner designed to return proactively in a natural rhythm.

- [Architecture and developer documentation](docs/index.html)
- [Product requirements](context/foundation/prd.md)
- [Roadmap](context/foundation/roadmap.md)
- [Test plan](context/foundation/test-plan.md) — named risks and the suites that defend them
- [Deployment runbook](context/foundation/deployment-runbook.md)

The repository contains two independent projects. There is no root workspace or shared build.

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

Vite serves the current React scaffold and proxies `/api` to the backend. Product UI and API
consumption are still planned.

`OPENROUTER_API_KEY` is optional for normal startup and hermetic tests. Inject it into the backend
process only when exercising a live LLM call; do not put it in a committed file.

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

# Repository Guidelines

2do AI is a personal planning application with a Spring Boot 4/Java 25 backend and a React 19/Vite/TypeScript frontend. The two directories are independent projects; there is no root workspace or shared build.

## Start Here

- Read `@CLAUDE.md` before changing code; it is the canonical source for project conventions, deployment constraints, and the user's explanation preferences. Use `@context/foundation/prd.md`, `@context/foundation/tech-stack.md`, and `@context/foundation/data-model.md` for product and architecture intent.
- Work test-first: add a failing JUnit 5 or Vitest test, implement the smallest change, then refactor.
- Flyway alone owns the PostgreSQL schema. Add backward-compatible `backend/src/main/resources/db/migration/V<n>__*.sql` migrations; never change Hibernate from `ddl-auto=validate` to create or update.
- Keep backend code under `com.thedariusz.todoai`. Domain aggregate IDs use UUID v7, domain tables use `timestamptz` audit columns, and database names are `snake_case`.
- Do not commit secrets or print environment values. `OPENROUTER_API_KEY` is injected externally. Confirm the undecided auth approach before implementing authentication flows.

## Build and Verification

Run backend commands from `backend/`: `mvn spring-boot:run`, `mvn test`, and `mvn package`. Target one test with `mvn test -Dtest=ClassName#methodName`.

Run frontend commands from `frontend/`: `npm run dev`, `npm test`, `npm run lint`, and `npm run build`. Target one file with `npx vitest run src/App.test.tsx`; use `npm run test:watch` while iterating.

Before committing, run the `/check` skill, which executes backend tests plus frontend tests, lint, and build. GitHub Actions repeats these gates before path-filtered deployment from `master`.

## Layout and Conventions

Backend production and test code live under `backend/src/main` and `backend/src/test`; mirror package paths in tests. Frontend source lives in `frontend/src`; co-locate React tests as `*.test.tsx`, with shared setup in `frontend/src/test/setup.ts`.

TypeScript is strict about unused values and fallthrough. ESLint's flat config is the formatter/linter; no Prettier is configured. Match existing Java tab indentation and frontend two-space indentation.

## Commits and Pull Requests

Use feature branches and Conventional Commit subjects such as `feat(scope):`, `fix(scope):`, `docs(scope):`, or `chore(scope):`. Keep PRs focused, describe behavior and verification, and merge through a PR to `master` only after all affected project gates pass.

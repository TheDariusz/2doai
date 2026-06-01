---
bootstrapped_at: 2026-05-28T19:40:15Z
starter_id: spring
starter_name: Spring Boot
project_name: 2doai
language_family: java
package_manager: maven
cwd_strategy: native-cwd
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: null
---

## Hand-off

Source: `context/foundation/tech-stack.md` (backend component of the 2doai monorepo).

```yaml
starter_id: spring
package_manager: maven
project_name: 2doai
hints:
  language_family: java
  team_size: solo
  deployment_target: fly
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
  path_taken: standard
  quality_override: false
  self_check_answers: null
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: true
  has_background_jobs: true
```

**Why this stack** (verbatim from hand-off body):

> Solo developer building a personal todo + AI-planning MVP after hours over ~7 weeks. Spring Boot is the recommended default for `(web, java)` and clears all four agent-friendly gates (typed, convention-based, popular in training data, well-documented) with bootstrapper confidence `verified`. The PRD's feature signals — multi-user auth (FR-001/002), AI integration for auto-tagging and proactive proposals (FR-008/011-014), and a natural-rhythm scheduler implying background jobs (FR-011) — all fit Spring's batteries-included shape (Spring Security, scheduling, REST). Deployment lands on Fly.io (Spring card's first default) — acceptable JVM cold-start for a personal MVP. CI on GitHub Actions with auto-deploy on merge to main matches the solo + short-timeline profile. Spring Boot scaffolds backend only; the PRD's PWA read-only requirement (FR-017) and shape-notes' monorepo intent mean a separate JS/TS frontend package will be added after bootstrap.

## Pre-scaffold verification

| Signal      | Value   | Severity | Notes                                                              |
| ----------- | ------- | -------- | ------------------------------------------------------------------ |
| npm package | not run | —        | non-JS starter (java); `cmd_template` invokes no `create-*` CLI    |
| GitHub repo | not run | —        | card `docs_url` (`docs.spring.io/spring-boot/`) is not a GitHub repo |

No recency signal available for this starter. Proceeded with no warning.

## Scaffold log

**Resolved invocation**: `curl -sSf 'https://start.spring.io/starter.tgz' -d dependencies=web,devtools -d type=maven-project -d javaVersion=21 -d groupId=com.example -d artifactId=2doai | tar -xzf - -C backend/`
**Strategy**: native-cwd (adapted)
**Exit code**: 0
**Pre-flight files-to-touch**: standard Spring Boot Maven layout (pom.xml, mvnw, mvnw.cmd, .mvn/, src/, HELP.md, .gitignore, .gitattributes)
**Files written by CLI**: ~10 (full Maven project tree, extracted flat into `backend/`)
**Pre-existing files preserved**: none (`backend/` was an empty directory)

**Adaptation note**: The Spring card's `cmd_template` scaffolds via `curl … start.spring.io | tar`, which extracts files *flat* into the working directory rather than creating a `{name}` subdirectory the way `create-*` CLIs do. The config default for an unlisted `starter_id` is `subdir-then-move`, but that mechanic does not fit a flat tar extraction. Resolved by treating it as `native-cwd` and extracting straight into the (empty) `backend/` folder via `tar -C`. The `{name}` placeholder was substituted with the hand-off `project_name` (`2doai`) instead of `.`/`.bootstrap-scaffold`, because for this card `{name}` is the Maven `artifactId` — the placeholder values would have produced an invalid `pom.xml`. Result: `pom.xml` `<artifactId>2doai</artifactId>`, Java 21, Spring Boot web + devtools.

**Note on generated package name**: Because the artifactId `2doai` begins with a digit (illegal as a Java package segment), the generator sanitized the base package to `com.example._doai`. The application class is `src/main/java/com/example/_doai/Application.java`. Functional but cosmetically awkward — rename the base package (and `groupId`/`artifactId` if desired) if a cleaner identifier is wanted.

## Post-scaffold audit

**Tool**: skipped — no built-in audit tool for java
**Recommended external tool**: OWASP Dependency-Check (Maven plugin `org.owasp:dependency-check-maven`) or Snyk for ongoing dependency vulnerability scanning.

## Hints recorded but not acted on

v1 surfaces these hints but takes no automated action on them (deferred to a future agent-context skill):

| Hint                    | Value               |
| ----------------------- | ------------------- |
| bootstrapper_confidence | verified            |
| quality_override        | false               |
| path_taken              | standard            |
| self_check_answers      | null                |
| team_size               | solo                |
| deployment_target       | fly                 |
| ci_provider             | github-actions      |
| ci_default_flow         | auto-deploy-on-merge |
| has_auth                | true                |
| has_payments            | false               |
| has_realtime            | false               |
| has_ai                  | true                |
| has_background_jobs     | true                |

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` (if you have not already) to start your own repo history.
- No `.scaffold` siblings were created (the target folder was empty), so there is nothing to reconcile.
- Java has no built-in dependency audit; wire up OWASP Dependency-Check or Snyk per your project's risk tolerance.
- Consider renaming the base package `com.example._doai` to a cleaner identifier, and set a real `groupId`/`artifactId` for your domain.
- The frontend component (`tech-stack-frontend.md`, Vite + React) is a separate bootstrap run into `frontend/`.

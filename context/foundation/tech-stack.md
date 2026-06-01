---
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
---

## Why this stack

Solo developer building a personal todo + AI-planning MVP after hours over ~7 weeks. Spring Boot is the recommended default for `(web, java)` and clears all four agent-friendly gates (typed, convention-based, popular in training data, well-documented) with bootstrapper confidence `verified`. The PRD's feature signals — multi-user auth (FR-001/002), AI integration for auto-tagging and proactive proposals (FR-008/011-014), and a natural-rhythm scheduler implying background jobs (FR-011) — all fit Spring's batteries-included shape (Spring Security, scheduling, REST). Deployment lands on Fly.io (Spring card's first default) — acceptable JVM cold-start for a personal MVP. CI on GitHub Actions with auto-deploy on merge to main matches the solo + short-timeline profile. Spring Boot scaffolds backend only; the PRD's PWA read-only requirement (FR-017) and shape-notes' monorepo intent mean a separate JS/TS frontend package will be added after bootstrap.

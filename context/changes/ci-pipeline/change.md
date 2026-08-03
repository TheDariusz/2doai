---
change_id: ci-pipeline
title: CI pipeline complementing CD — tests, lint, typecheck, build, Trivy scans, agentic AI code review
status: implementing
created: 2026-08-03
updated: 2026-08-03
archived_at: null
---

## Notes

let's start creating a good CI that will complement the current CD process.
I want to have in my CI:
- agentinc AI code review (regular and security) with OpenRouter gate and one of the model (Idk yet wich model we will use)
- trivy image and code review
- standard steps like tests, lint, typecheck, build

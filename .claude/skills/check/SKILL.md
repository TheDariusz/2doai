---
name: check
description: Run the full quality gate for 2do AI — backend tests plus frontend lint and build. Use before committing, before opening a PR, or to confirm both halves of the project are green after changes.
---

Run the project's quality gate across both sub-projects and report results. Run all three steps even if an earlier one fails, then summarize which passed and which failed (with the failing output).

1. **Backend tests** — from `backend/`:
   ```
   mvn test
   ```
2. **Frontend lint** — from `frontend/`:
   ```
   npm run lint
   ```
3. **Frontend build + typecheck** — from `frontend/`:
   ```
   npm run build
   ```

Report a concise PASS/FAIL per step. If anything fails, show the relevant error output and stop short of claiming the project is green. Do not commit or push — this skill only verifies.

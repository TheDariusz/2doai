#!/usr/bin/env bash
# Gate predicate for the agentic security review.
#
# SINGLE SOURCE OF TRUTH: ai-review.yml calls this file and ai-review-gate.test.sh
# tests this file, so the gate that runs and the gate that is tested cannot drift.
#
# Fails CLOSED only on a successfully parsed, high-confidence, high-severity
# security finding. Every other outcome — missing file, malformed JSON, prose
# instead of JSON, empty body, unevaluable findings — logs a warning and exits 0.
# That asymmetry is deliberate: an OpenRouter outage must not block a merge at
# 11pm, and a stochastic reviewer only earns the right to block when it has
# produced something unambiguous.
set -uo pipefail

FILE="${1:?usage: ai-review-gate.sh <findings.json>}"

warn() {
  echo "::warning title=AI security gate::$1"
  exit 0
}

[ -s "$FILE" ] || warn "no findings at '$FILE' (missing or empty) — not gating"

# TWO INDEPENDENT KEYS. Severity alone is trivially inflated by a model — it
# costs it nothing to call everything "high" — so a finding must ALSO be
# security-category and self-report >= 0.8 confidence before it blocks.
#
# The `(.confidence | type) == "number"` guard matters: jq orders numbers before
# strings, so a confidence of "0.9" (string) would compare >= 0.8 as true. The
# guard makes a malformed confidence fail OPEN, matching the rest of the script.
GATING=$(jq -c '
  [ .findings[]?
    | select(type == "object")
    | select(
        .category == "security"
        and .severity == "high"
        and (.confidence | type) == "number"
        and .confidence >= 0.8
      )
  ]' "$FILE" 2>/dev/null) || warn "response is not a findings object — not gating"

COUNT=$(jq 'length' <<<"$GATING" 2>/dev/null) || warn "could not evaluate findings — not gating"

if [ "$COUNT" -eq 0 ]; then
  echo "No high-confidence high-severity security findings. Gate passes."
  exit 0
fi

echo "::error title=AI security review::${COUNT} high-confidence high-severity security finding(s) — merge blocked"
jq -r '.[] | "  \(.file):\(.line) — \(.title) (confidence \(.confidence))"' <<<"$GATING"
exit 1

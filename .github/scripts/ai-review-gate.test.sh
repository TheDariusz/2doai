#!/usr/bin/env bash
# Exercises ai-review-gate.sh against the committed fixtures.
#
# The gate predicate is the only genuinely new logic in the AI review — a
# stochastic input meeting a deterministic rule — so it gets the one runnable
# check. No network, no key, no framework: just the real gate script and five
# JSON files. Wired into repo-checks.yml so it cannot rot unnoticed.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATE="$HERE/ai-review-gate.sh"
FIXTURES="$HERE/../ai-review-fixtures"

fail=0

expect() { # <expected-exit> <fixture> <why>
  local want="$1" fixture="$2" why="$3" got
  "$GATE" "$FIXTURES/$fixture" >/dev/null 2>&1
  got=$?
  if [ "$got" -ne "$want" ]; then
    echo "FAIL  $fixture — expected exit $want, got $got  ($why)"
    fail=1
  else
    echo "ok    $fixture — exit $got  ($why)"
  fi
}

expect 1 gating.json         "high + security + 0.9 blocks the merge"
expect 0 low-confidence.json "high + security + 0.5 is below the confidence floor"
expect 0 style-only.json     "high + 0.95 but category style — severity alone must not block"
expect 0 empty.json          "no findings is the common case and must stay green"
expect 0 malformed.json      "prose instead of JSON fails open, never closed"

if [ "$fail" -eq 0 ]; then
  echo "All gate predicate cases passed."
else
  echo "Gate predicate regression — see failures above."
fi
exit "$fail"

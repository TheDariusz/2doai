#!/usr/bin/env bash
# Exercises ai-review-call.sh with no network, no key and no framework.
#
# The contract worth pinning is the ASYMMETRY: the world misbehaving exits 0,
# our own misconfiguration exits 1. Both directions regress silently — flip a
# die() back to a warn() and the required check goes green while reviewing
# nothing, which is exactly what it did on four consecutive runs before
# OPENROUTER_CI_KEY existed. Nothing else in CI would have noticed.
#
# The second half pins the request body. A regression there is worse than a
# crash: drop the schema wiring or let `model` drift from `models[0]` and the
# script still exits 0 and still looks like it reviewed the diff.
#
# `curl` is shimmed onto PATH, so this needs no secret and costs nothing.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CALL="$HERE/ai-review-call.sh"
SCHEMA="$HERE/../ai-review-schema.json"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cd "$WORK" || exit 1

echo 'diff --git a/x b/x' >diff.txt
: >empty.txt
mkdir shim
cat >shim/curl <<EOS
#!/usr/bin/env bash
# Canned OpenRouter response. The sentinel NETFAIL simulates curl's own failure
# (exit 7) rather than a bad response body.
read -r first <"$WORK/response" || true
[ "\$first" = NETFAIL ] && exit 7
cat "$WORK/response"
EOS
chmod +x shim/curl

respond() { printf '%s' "$1" >"$WORK/response"; }
respond NETFAIL

fail=0

expect() { # <want-exit> <mode> <diff-file> <why> [VAR=VAL ...]
  local want="$1" mode="$2" diff="$3" why="$4" got
  shift 4
  env PATH="$WORK/shim:$PATH" GITHUB_STEP_SUMMARY=/dev/null "$@" \
    bash "$CALL" "$mode" "$diff" out.json >/dev/null 2>&1
  got=$?
  if [ "$got" -ne "$want" ]; then
    echo "FAIL  expected exit $want, got $got  ($why)"
    fail=1
  else
    echo "ok    exit $got  ($why)"
  fi
}

echo "-- our configuration is wrong: must fail CLOSED --"
expect 1 security diff.txt "missing key is a config bug, not an outage — must not report green" \
  OPENROUTER_CI_KEY= AI_MODELS=a/b
expect 1 security diff.txt "empty AI_MODELS is a config bug" \
  OPENROUTER_CI_KEY=k AI_MODELS=
expect 1 security diff.txt "unset AI_MODELS must annotate, not die on set -u" \
  OPENROUTER_CI_KEY=k
expect 1 bogus diff.txt "unknown mode is a programming bug" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b

echo "-- the world is wrong: must fail OPEN --"
respond NETFAIL
expect 0 security diff.txt "curl network failure or timeout" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
respond '{"error":{"message":"rate limited"}}'
expect 0 security diff.txt "OpenRouter 429/error body" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
respond '<html>502 Bad Gateway</html>'
expect 0 security diff.txt "gateway HTML instead of JSON" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
respond '{"choices":[{"message":{"content":"I am happy to review this!"}}]}'
expect 0 security diff.txt "model returns prose instead of the schema" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
respond '{"choices":[{"message":{"content":null}}]}'
expect 0 security diff.txt "model refusal (null content)" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
expect 0 security empty.txt "empty diff is nothing to review, not a failure" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b

echo "-- the request body still says what we think it says --"
respond '{"choices":[{"message":{"content":"{\"findings\":[]}"}}]}'
env PATH="$WORK/shim:$PATH" GITHUB_STEP_SUMMARY=/dev/null \
  OPENROUTER_CI_KEY=k AI_MODELS=x/one,y/two \
  bash "$CALL" security diff.txt out.json >/dev/null 2>&1

assert_jq() { # <why> <jq-filter> [file]
  if jq -e "$2" "${3:-request.json}" >/dev/null 2>&1; then
    echo "ok    $1"
  else
    echo "FAIL  $1"
    fail=1
  fi
}

assert_jq "models[] carries the whole fallback chain, in order" '.models == ["x/one","y/two"]'
assert_jq "model repeats models[0], as OpenRouter requires" '.model == .models[0]'
assert_jq "the diff is wrapped in the <diff> trust boundary" \
  '.messages[1].content | startswith("<diff>\n") and endswith("\n</diff>")'
assert_jq "response_format still carries the committed schema" \
  "$(printf '.response_format.json_schema.schema == %s' "$(cat "$SCHEMA")")"
assert_jq "structured output is strict" '.response_format.json_schema.strict == true'
assert_jq "a well-formed response is written through to the out file" '.findings == []' out.json

if [ "$fail" -eq 0 ]; then
  echo "All call-script cases passed."
else
  echo "Call-script regression — see failures above."
fi
exit "$fail"

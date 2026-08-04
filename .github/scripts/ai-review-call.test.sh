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
# Behave like curl: append the status ONLY when -w asks for it. Volunteering it
# unconditionally would let the script stop passing -w with every test still
# green, while in production the status parse would take the last line of the
# JSON body instead and fail every call open. (Found by mutation testing.)
for a in "\$@"; do
  [ "\$a" = -w ] && { printf '\n%s' "\$(cat "$WORK/status")"; break; }
done
EOS
chmod +x shim/curl

respond() { # <body> [http-status]
  printf '%s' "$1" >"$WORK/response"
  printf '%s' "${2:-200}" >"$WORK/status"
}
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
respond '<html>502 Bad Gateway</html>' 502
expect 0 security diff.txt "gateway HTML on a 502 — reported as a status, not as an empty body" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
respond '{"error":{"message":"forbidden"}}' 403
expect 0 security diff.txt "403 with a JSON error body" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
respond '{"choices":[{"message":{"content":"I am happy to review this!"}}]}'
expect 0 security diff.txt "model returns prose instead of the schema" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
respond '{"choices":[{"message":{"content":null},"finish_reason":"length"}]}'
expect 0 security diff.txt "max_tokens exhausted mid-answer (finish_reason: length)" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b
expect 0 security empty.txt "empty diff is nothing to review, not a failure" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b

echo "-- wrong shape must not reach the gate looking like a clean review --"
assert_empty_out() { # <why>
  if [ -s out.json ]; then
    echo "FAIL  $1 — out.json has $(wc -c <out.json) bytes; the gate would read it as a real review"
    fail=1
  else
    echo "ok    $1"
  fi
}

respond '{"choices":[{"message":{"content":"{\"summary\":\"looks good\"}"}}]}'
expect 0 security diff.txt "a JSON object with no findings key" OPENROUTER_CI_KEY=k AI_MODELS=a/b
assert_empty_out "  ...and it is not written through to the out file"

respond '{"choices":[{"message":{"content":"{\"findings\":\"not-an-array\"}"}}]}'
expect 0 security diff.txt "findings as a string, not an array" OPENROUTER_CI_KEY=k AI_MODELS=a/b
assert_empty_out "  ...and it is not written through to the out file"

respond '{"choices":[{"message":{"content":"{\"findings\":[]}{\"findings\":[]}"}}]}'
expect 0 security diff.txt "two concatenated JSON documents" OPENROUTER_CI_KEY=k AI_MODELS=a/b
assert_empty_out "  ...and it is not written through to the out file"

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

echo "-- the run summary still explains a response it could not parse --"
# This is the half that used to blank out exactly when it mattered: jq exits 5
# with empty stdout on an unparseable body, so an in-filter `// "unknown"` never
# ran and the summary rendered empty backticks and a bare `$`.
respond '<html>502 Bad Gateway</html>' 502
env PATH="$WORK/shim:$PATH" GITHUB_STEP_SUMMARY="$WORK/summary.md" \
  OPENROUTER_CI_KEY=k AI_MODELS=a/b \
  bash "$CALL" security diff.txt out.json >/dev/null 2>&1

assert_grep() { # <why> <literal>
  if grep -qF -- "$2" "$WORK/summary.md"; then # `--`: every pattern here starts with a dash
    echo "ok    $1"
  else
    echo "FAIL  $1 — summary has no '$2'"
    fail=1
  fi
}
# These patterns are literal text to match, not strings to expand: the `$` in
# `$unknown` and the backticks around `502` are exactly what we assert the
# summary renders. Single quotes are the point here, so SC2016 is noise.
# shellcheck disable=SC2016
{
  assert_grep "summary records the HTTP status" '- http: `502`'
  assert_grep "served-by falls back to unknown instead of empty backticks" '- served by: `unknown`'
  assert_grep "finish reason is recorded" '- finish reason: `unknown`'
  assert_grep "cost falls back instead of rendering a bare dollar sign" '- cost: $unknown'
}

if [ "$fail" -eq 0 ]; then
  echo "All call-script cases passed."
else
  echo "Call-script regression — see failures above."
fi
exit "$fail"

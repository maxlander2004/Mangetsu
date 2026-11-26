#!/usr/bin/env bash
# Checks the release tag guard used by .github/workflows/release.yml.
#
# Usage: bash scripts/release-tag-check-test.sh
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
checker="$here/release-tag-check.sh"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
printf 'val _versionName = "1.10.4"\n' > "$tmp"

fail=0

expect() {
  local want="$1"
  shift
  local got=0
  bash "$checker" "$@" > /dev/null 2>&1 || got=$?
  if [ "$got" -ne "$want" ]; then
    echo "FAIL: [$*] exited $got, want $want"
    fail=1
  else
    echo "ok: [$*] exited $got"
  fi
}

expect 0 debug-1.10.4 "$tmp"
expect 1 debug-1.10.3 "$tmp"
expect 2 "" "$tmp"
expect 2 debug-1.10.4 "$tmp-does-not-exist"

if [ "$fail" -ne 0 ]; then
  echo "release tag guard: FAILED"
  exit 1
fi

echo "release tag guard: all cases passed"

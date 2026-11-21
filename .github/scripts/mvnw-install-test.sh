#!/bin/bash
# Smoke tests for the hardened mvnw install path (bead ddl-s47.13).
#
# CI's normal build only exercises the happy path (one process, unzip present,
# a warm wrapper cache). These tests cover the paths that would otherwise fail
# only for a user:
#   1. two concurrent fresh runs share MAVEN_USER_HOME -> exactly one complete
#      install, and no staging directory or lock is left behind;
#   2. a broken cache (directory without an executable launcher) is removed and
#      reinstalled under the lock, still leaving one distribution.
#
# The missing-unzip and multi-candidate branches need a controlled archive or
# container and are covered by the installer's own messages, not here.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT HUP INT TERM

# A private wrapper cache, so the test never touches the runner's ~/.m2.
export MAVEN_USER_HOME="$work/m2"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

run_mvnw() {
    timeout 300 "$repo_root/mvnw" "$@"
}

install_count() {
    find "$MAVEN_USER_HOME/wrapper/dists" -mindepth 2 -maxdepth 2 -type d \
        -exec test -x {}/bin/mvn \; -print 2>/dev/null | wc -l | tr -d ' '
}

leftovers() {
    find "$MAVEN_USER_HOME" \( -name '.mvnw-install.*' -o -name '*.lock' \) \
        -print -quit 2>/dev/null
}

# 1. Concurrent fresh installs.
run_mvnw -v >"$work/first.log" 2>&1 &
first=$!
run_mvnw -v >"$work/second.log" 2>&1 &
second=$!
wait "$first" || { cat "$work/first.log"; fail "first concurrent run failed"; }
wait "$second" || { cat "$work/second.log"; fail "second concurrent run failed"; }
[ "$(install_count)" -eq 1 ] \
    || fail "expected one installed distribution, found $(install_count)"
[ -z "$(leftovers)" ] || fail "staging or lock leftovers remain: $(leftovers)"
echo "ok: concurrent installs produced one distribution with no leftovers"

# 2. Broken cache removal and reinstall.
install="$(find "$MAVEN_USER_HOME/wrapper/dists" -mindepth 2 -maxdepth 2 -type d \
    -exec test -x {}/bin/mvn \; -print | head -n 1)"
[ -n "$install" ] || fail "the installed distribution was not found"
rm -f "$install/bin/mvn"
run_mvnw -v >"$work/broken.log" 2>&1 \
    || { cat "$work/broken.log"; fail "run with a broken cache failed"; }
[ -x "$install/bin/mvn" ] || fail "the broken cache was not reinstalled"
[ "$(install_count)" -eq 1 ] \
    || fail "the broken-cache reinstall left duplicate distributions"
[ -z "$(leftovers)" ] || fail "staging or lock leftovers remain: $(leftovers)"
echo "ok: broken cache removed and reinstalled in place"

echo "mvnw install tests passed"

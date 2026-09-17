#!/bin/sh
# Start the local development database.
#
# Usage: scripts/start-local-db.sh
#
# This only starts the containers and waits for the database to be healthy; it
# does not apply the Liquibase changelog (the compose `liquibase` service does).

set -eu

script_path="$0"
if command -v readlink >/dev/null 2>&1; then
    resolved="$(readlink -f "$script_path" 2>/dev/null || true)"
    [ -n "$resolved" ] && script_path="$resolved"
fi
repo_root="$(cd "$(dirname "$script_path")/.." && pwd)"

cd "$repo_root"

if ! command -v docker >/dev/null 2>&1; then
    echo "docker is required but was not found on PATH" >&2
    exit 1
fi

docker compose up -d --wait

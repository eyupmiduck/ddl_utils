#!/bin/sh
# Start the local development database (and apply migrations if needed).
#
# Usage: scripts/start-local-db.sh

set -eu

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

cd "$repo_root"
docker compose up -d

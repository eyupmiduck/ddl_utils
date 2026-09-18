#!/bin/sh
# Stop the local development database, keeping its data volume.
#
# Usage: scripts/stop-local-db.sh

set -eu

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

cd "$repo_root"

# `down` keeps named volumes by default; do not add -v here. Use
# scripts/refresh-local-db.sh, which wipes the data on purpose.
docker compose down

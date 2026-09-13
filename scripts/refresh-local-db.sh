#!/bin/sh
# Reset the local development database: drop the data volume and re-apply
# the Liquibase changelog from scratch.
#
# Usage: scripts/refresh-local-db.sh

set -eu

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

cd "$repo_root"
docker compose down -v
docker compose up -d

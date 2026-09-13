#!/usr/bin/env bash
# Auto-fix SQL style issues in the Liquibase changelog with SQLFluff.
#
# Usage: scripts/sqlfluff-fix.sh
#
# Uses the repo's .venv sqlfluff if present, otherwise falls back to
# `sqlfluff` on PATH. Only fixes what SQLFluff can fix automatically;
# remaining violations are reported and must be fixed by hand.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sql_dir="$repo_root/ddl_utils/src/main/resources/db/changelog"

if [[ -x "$repo_root/.venv/bin/sqlfluff" ]]; then
    sqlfluff="$repo_root/.venv/bin/sqlfluff"
else
    sqlfluff="sqlfluff"
fi

"$sqlfluff" fix "$sql_dir"

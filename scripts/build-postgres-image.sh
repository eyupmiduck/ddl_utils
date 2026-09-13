#!/bin/sh
# Build the custom PostgreSQL image with the application roles baked in.
#
# Usage: scripts/build-postgres-image.sh <base-image>
#   e.g. scripts/build-postgres-image.sh postgres:17-alpine
#   builds ddl-utils-postgres:17-alpine
#
# The resulting image tag is what the `postgres.image` Maven property (and
# the compose POSTGRES_IMAGE variable) should point at.

set -eu

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
base="${1:?base image required, e.g. postgres:17-alpine}"
tag="ddl-utils-${base}"

docker build -t "$tag" --build-arg BASE_IMAGE="$base" "$repo_root/docker/postgres"
echo "Built $tag"

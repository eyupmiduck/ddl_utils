# Custom PostgreSQL image with the application roles baked in.
#
# The roles init script runs on first container init (before Liquibase), so
# the ddl_utils_owner / ddl_utils_caller / ddl_utils_test roles exist in any
# database created from this image. This is also the place to install
# non-standard PostgreSQL extensions later.
#
# Build with: scripts/build-postgres-image.sh postgres:17-alpine

ARG BASE_IMAGE=postgres:17-alpine
FROM ${BASE_IMAGE}

COPY roles.sql /docker-entrypoint-initdb.d/roles.sql

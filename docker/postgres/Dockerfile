# Custom PostgreSQL image with the application roles and the plpgsql_check
# extension baked in.
#
# The roles init script runs on first container init (before Liquibase), so
# the ddl_utils_owner / ddl_utils_caller / ddl_utils_test roles exist in any
# database created from this image.
#
# Build with: scripts/build-postgres-image.sh postgres:17-alpine

ARG BASE_IMAGE=postgres:17-alpine
FROM ${BASE_IMAGE}

# plpgsql_check is not packaged for the PostgreSQL build the official image
# installs (Alpine's package targets a different major), so build it from
# source against the image's own server headers. The tag is pinned and its
# SHA-256 verified; bitcode generation is skipped (no LLVM in the image).
ARG PLPGSQL_CHECK_VERSION=2.10.10
RUN apk add --no-cache --virtual .plpgsql-check-build build-base wget \
    && wget -qO /tmp/plpgsql_check.tar.gz \
        "https://github.com/okbob/plpgsql_check/archive/refs/tags/v${PLPGSQL_CHECK_VERSION}.tar.gz" \
    && echo "19d5e460c83ba330469fc14074085fc1849a169b8b980b761a7bb7149d4d44ed  /tmp/plpgsql_check.tar.gz" | sha256sum -c - \
    && mkdir -p /tmp/plpgsql_check \
    && tar -xzf /tmp/plpgsql_check.tar.gz -C /tmp/plpgsql_check --strip-components=1 \
    && make -C /tmp/plpgsql_check USE_PGXS=1 with_llvm=no -j"$(nproc)" \
    && make -C /tmp/plpgsql_check USE_PGXS=1 with_llvm=no install \
    && apk del .plpgsql-check-build \
    && rm -rf /tmp/plpgsql_check*

# The entrypoint runs /docker-entrypoint-initdb.d scripts only when the data
# directory is initialized for the first time; a pre-existing volume is left
# untouched (and may lack the roles).
COPY --chown=postgres:postgres --chmod=0644 roles.sql /docker-entrypoint-initdb.d/roles.sql

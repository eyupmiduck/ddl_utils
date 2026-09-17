# ddl_utils

utilities for postgres ddl to reduce locking nightmares

## Changelog validation dependency

Changelog validation (changeset and SQL naming, and orphaned SQL files) lives in the
[`liquibase_validation`](https://github.com/eyupmiduck/liquibase_validation)
project and is consumed as the test-scoped `io.github.eyupmiduck:liquibase-validation`
artifact from GitHub Packages. GitHub Packages requires authentication even
for public packages, so a classic personal access token with the
`read:packages` scope must be configured under the `github` server id in
`~/.m2/settings.xml` for local builds. See that project's README for the
settings snippet and the CI access requirements.

## Local development database

Spin up a local PostgreSQL with the `ddl_utils` schema applied, so you can
log in and experiment:

```sh
docker compose up -d
```

This starts a `ddl-utils-postgres:17-alpine` container (build it first with
`scripts/build-postgres-image.sh postgres:17-alpine`) and runs Liquibase
against it as the `ddl_utils_owner` role to apply the changelog. Connect
with:

```sh
psql -h localhost -p 5432 -U postgres -d ddl_utils
# password: postgres
```

Or any client with JDBC URL
`jdbc:postgresql://localhost:5432/ddl_utils` (user/password `postgres`).

Data persists in a named Docker volume (`ddl_utils_pgdata`), so it survives
`docker compose down`, container restarts, and laptop reboots. To stop and
later resume with your data intact:

```sh
docker compose down      # stop, keep data
docker compose up -d     # restart, data still there
```

To reset everything (drop the volume and re-apply migrations):

```sh
docker compose down -v && docker compose up -d
```

`scripts/refresh-local-db.sh` does the same reset in one step. Only `down -v`
(or the refresh script) deletes data.

Convenience scripts:

```sh
scripts/start-local-db.sh    # docker compose up -d
scripts/stop-local-db.sh     # docker compose down (keeps data)
scripts/refresh-local-db.sh  # down -v + up -d (wipes data)
```

The port (5432) and credentials are set in `compose.yaml`; change them there
if they conflict with an existing local PostgreSQL.

## Custom PostgreSQL image

The build and local dev database use a custom image (`ddl-utils-postgres:<ver>-alpine`)
built from the official `postgres:<ver>-alpine` image. It bakes in a roles
init script (`docker/postgres/roles.sql`) that creates the application roles
before Liquibase runs:

- `ddl_utils_owner` — owns the schema and objects; Liquibase connects as this
  role to load the schema (never as `postgres`).
- `ddl_utils_caller` — the role privileges are granted to (e.g. `SELECT` on
  the lock-settings tables and `EXECUTE` on the routines).
- `ddl_utils_test` — granted `ddl_utils_caller`; used by the integration tests.

This is also where non-standard PostgreSQL extensions would be installed.

Build it for a given base version:

```sh
scripts/build-postgres-image.sh postgres:17-alpine   # -> ddl-utils-postgres:17-alpine
```

## Running against a different PostgreSQL version

The PostgreSQL image is a single source of truth controlled by the
`postgres.image` Maven property. It is used for jOOQ code generation, the
integration tests, and the local dev database. It must be a custom image tag,
so build it first. Override it in any of these ways (highest precedence
first):

```sh
# Build the custom image, then run the build/tests
scripts/build-postgres-image.sh postgres:16-alpine
./mvnw verify -Dpostgres.image=ddl-utils-postgres:16-alpine

# Environment variable (tests + jOOQ codegen)
POSTGRES_IMAGE=ddl-utils-postgres:16-alpine ./mvnw verify

# Local dev database (Docker Compose)
POSTGRES_IMAGE=ddl-utils-postgres:16-alpine docker compose up -d
```

The default is `ddl-utils-postgres:17-alpine`. CI builds the custom image and
runs the full build against PostgreSQL 16, 17 and 18 (see
`.github/workflows/maven.yml`).

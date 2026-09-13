# ddl_utils

utilities for postgres ddl to reduce locking nightmares

## Local development database

Spin up a local PostgreSQL with the `ddl_utils` schema applied, so you can
log in and experiment:

```sh
docker compose up -d
```

This starts a `postgres:17-alpine` container and runs Liquibase against it to
apply the changelog. Connect with:

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

## Running against a different PostgreSQL version

The PostgreSQL image is a single source of truth controlled by the
`postgres.image` Maven property. It is used for jOOQ code generation, the
integration tests, and the local dev database. Override it in any of these
ways (highest precedence first):

```sh
# Command line (tests + jOOQ codegen)
./mvnw verify -Dpostgres.image=postgres:16-alpine

# Environment variable (tests + jOOQ codegen)
POSTGRES_IMAGE=postgres:16-alpine ./mvnw verify

# Local dev database (Docker Compose)
POSTGRES_IMAGE=postgres:16-alpine docker compose up -d
```

The default is `postgres:17-alpine`. CI runs the full build against both
PostgreSQL 16 and 17 (see `.github/workflows/maven.yml`).

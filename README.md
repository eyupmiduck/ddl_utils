# ddl_utils

Lock-aware DDL helpers for PostgreSQL, packaged as Liquibase-managed PL/pgSQL
routines. They run `ALTER TABLE` work (adding columns, altering tables) against
production-sized databases without holding a schema lock for an unbounded time,
with lock settings that cascade table → schema → database.

The deliverable is the Liquibase changelog under
`ddl_utils/src/main/resources/db/changelog/`; the Java sources are only the
JUnit 5 / Testcontainers harness that exercises those routines. You install
ddl_utils with the Liquibase CLI, without building anything or running Docker
(see [Installing with the Liquibase CLI](#installing-with-the-liquibase-cli)).

## Why: DDL locks are brutal

PostgreSQL acquires an `ACCESS EXCLUSIVE` lock for almost every `ALTER TABLE`
subcommand — **including metadata-only operations** like `ADD COLUMN`,
`DROP COLUMN`, or `SET DEFAULT`. Many developers assume a metadata change is
cheap and lock-free because it does not rewrite the table (PostgreSQL 11+
avoids the rewrite for constant defaults), but the lock is still exclusive and
still has to wait for every in-flight reader and writer to finish.

Worse, once an `ACCESS EXCLUSIVE` request is *waiting*, it blocks new readers
and writers too. PostgreSQL grants locks roughly in request order so a big
request cannot be starved, so a queued `ALTER TABLE` sits in front of ordinary
DML that would otherwise be compatible with the currently-held locks. One
idle transaction is then enough to stall the whole table.

### A three-session demo

```sql
-- setup
CREATE TABLE public.orders
(
    id integer PRIMARY KEY
);
```

| Session 1                                    | Session 2                                         | Session 3                                    |
|----------------------------------------------|---------------------------------------------------|----------------------------------------------|
| `BEGIN;`                                     |                                                   |                                              |
| `INSERT INTO public.orders (id) VALUES (1);` |                                                   |                                              |
| *(not committed)*                            |                                                   |                                              |
|                                              | `ALTER TABLE public.orders ADD COLUMN note text;` |                                              |
|                                              | *(hangs: waits for session 1's row lock)*         |                                              |
|                                              |                                                   | `INSERT INTO public.orders (id) VALUES (2);` |
|                                              |                                                   | *(also hangs — behind session 2)*            |

Session 1 holds a `ROW EXCLUSIVE` lock on `public.orders` until it commits or
rolls back. Session 2's `ALTER TABLE` needs `ACCESS EXCLUSIVE`, which conflicts,
so it waits. Session 3's `INSERT` only needs `ROW EXCLUSIVE`, which is
compatible with session 1's lock — but because session 2's conflicting request
is already waiting, session 3 queues behind it. Nothing on the table moves until
session 1 ends, at which point session 2 runs briefly and session 3 proceeds.

What this project adds is a bounded wait: instead of letting an `ALTER TABLE`
block forever (and block everyone behind it), these routines set a short
`lock_timeout`, give up if the lock cannot be taken, sleep, and retry up to a
total budget. A retry that has given up leaves the queue empty, so queued DML
gets through between attempts.

```sql
-- bounded wait: fails fast after 250 ms and retries, rather than hanging
SELECT ddl_utils_lib.add_column('public', 'orders', 'note', 'text', true, NULL, 250, 500, 30000);
```

The three settings are:

- `ddl_lock_timeout` — per-attempt `lock_timeout` in ms (`0` disables the timeout).
- `sleep_time` — ms to sleep between attempts.
- `statement_duration` — total ms budget; the call raises `55P03` once exceeded.

## What is in the box

Liquibase loads two schemas:

- **`ddl_utils`** — the application surface:
    - the `database_lock_settings` (single row), `schema_lock_settings`, and
      `table_lock_settings` tables, with `get_*`, `set_*`, and `clear_*`
      accessors;
    - `get_lock_settings(schema, table)`, which resolves the effective settings
      with the table → schema → database fallback;
    - lock-aware wrappers that read the settings for you (see the list below);
    - the shared `non_null_text`, `non_negative_integer`, `non_null_boolean`, and
      array domains used to validate inputs.
- **`ddl_utils_lib`** — generic helpers that take the settings explicitly (the
  same operations, plus `validate_constraint` and `has_top_level_comma`).
  `alter_table` is the internal runner they build on: it is the only routine
  that executes dynamic SQL, and callers should prefer the structured
  operations so the fragment is built from validated identifiers.

The user-facing helpers cover `ALTER TABLE` work that blocks concurrent DML —
it takes `ACCESS EXCLUSIVE` (all of these except `add_foreign_key`) or
`SHARE ROW EXCLUSIVE` (`add_foreign_key`) — so the bounded wait is worth
applying. Operations that take only `SHARE UPDATE EXCLUSIVE` and so never block
DML are deliberately not wrapped (see `validate_constraint` below):

- **Columns**: `add_column(s)` (with an optional default), `drop_column(s)`,
  `rename_column`, `set_column_default`, `drop_column_default`, `drop_not_null`,
  `set_not_null`, `set_column_storage`, `set_column_compression`,
  `drop_expression`, `add_identity`, `drop_identity`.
- **Constraints**: `add_check_constraint` and `add_foreign_key` (both emitted as
  `NOT VALID`, so no scan), `drop_constraint`, `rename_constraint`,
  `add_primary_key_using_index`, `add_unique_constraint_using_index` (attach an
  index built with `CREATE UNIQUE INDEX CONCURRENTLY`). The scan step is
  `ddl_utils_lib.validate_constraint` (it takes only `SHARE UPDATE EXCLUSIVE`,
  so it has no lock-aware wrapper and is used by the procedures).
- **Tables**: `rename_table`.

`ALTER COLUMN TYPE`, `SET LOGGED`, `SET TABLESPACE` and the other rewriting
operations are deliberately not offered. A function must make a single
`ALTER TABLE` call because it cannot commit mid-call, so an operation that needs
several `ALTER TABLE` calls to release each lock (for example making a column
`NOT NULL` without holding `ACCESS EXCLUSIVE` across the verification scan) is
implemented as a stored **procedure** in the `ddl_utils` schema, which can
`COMMIT` between steps:

- `ensure_not_null(schema, table, column)` — add a `NOT VALID` check, validate
  it, `SET NOT NULL`, drop the temporary constraint, committing between steps.
- `ensure_check_constraint(schema, table, constraint, expression)` — add a
  `CHECK` as `NOT VALID`, then validate.
- `ensure_foreign_key(...)` — the same for a foreign key.

Procedures are idempotent and recoverable: each step inspects the catalog and
skips work already committed, so a call interrupted part-way is completed by
calling it again. Because a procedure commits, it must run in autocommit (`CALL` inside a client transaction fails with `invalid transaction
termination`). See
[`procedures/README.md`](ddl_utils/src/main/resources/db/changelog/changes/procedures/README.md).

Most helpers exist twice: a `ddl_utils` lock-aware wrapper that resolves the
settings itself, and a matching `ddl_utils_lib` function that takes the three
settings explicitly. `validate_constraint` is the exception — it does not block
DML, so it exists only in `ddl_utils_lib`. The accessors, getters and DDL
helpers are `SECURITY INVOKER`. The setters and clearers are `SECURITY DEFINER`, because
`ddl_utils_caller` (the application role) only has `SELECT` on the settings
tables. See
[`functions/README.md`](ddl_utils/src/main/resources/db/changelog/changes/functions/README.md)
for every signature and purpose.

## Installing with the Liquibase CLI

Installing ddl_utils means applying the bundled Liquibase changelog to your
database. There is nothing to compile or package: clone the repository and point
the Liquibase CLI at `db.changelog-master.xml`.

### Prerequisites

- PostgreSQL 16, 17 or 18 (CI builds and tests all three; 17 is the default).
- Liquibase 5.0.x on your `PATH` (it needs Java 17+). Liquibase 5 ships without
  database drivers, so add the PostgreSQL driver once:

  ```sh
  liquibase lpm add postgresql --global
  ```

- A login role that owns the objects (`ddl_utils_owner` below) with `CREATE` on
  the target database and schema.
- A role the routines and settings are granted to (`ddl_utils_caller` below).
  The last changeset grants to it, so it must exist **before** you run the
  changelog. Applications connect as this role (or a role granted it).

### 1. Create the roles

Connect to the target database as a superuser (or a role that can `CREATE ROLE`)
and create the owner and caller roles:

```sql
CREATE ROLE ddl_utils_owner LOGIN PASSWORD 'change-me';
CREATE ROLE ddl_utils_caller; -- no login; the routines are granted to it

GRANT CREATE ON DATABASE mydb TO ddl_utils_owner;
\connect mydb
GRANT CREATE ON SCHEMA public TO ddl_utils_owner;
```

`ddl_utils_owner` needs `CREATE` on the database and schema so Liquibase can
create its tracking tables and the `ddl_utils` / `ddl_utils_lib` schemas. Never
run the migration as `postgres`.

### 2. Run the changelog

From the repository root, connect as the owner:

```sh
git clone https://github.com/eyupmiduck/ddl_utils.git
cd ddl_utils

liquibase \
  --url=jdbc:postgresql://localhost:5432/mydb \
  --username=ddl_utils_owner \
  --password=change-me \
  --changelog-file=ddl_utils/src/main/resources/db/changelog/db.changelog-master.xml \
  update
```

`liquibase status` reports pending changesets; re-running `update` is safe (Liquibase skips changesets it has already
applied).

### 3. Verify

```sql
SELECT ddl_utils.get_database_lock_settings();
SELECT ddl_utils.get_lock_settings('public', 'orders');
```

### Rolling back

Every changeset ships a rollback. Pass the same connection flags as above and
replace `update` with `rollback-count --count=<n>` to undo the last `n`
changesets (the routines and schemas are dropped; the roles from step 1 are left
in place):

```sh
liquibase \
  --url=jdbc:postgresql://localhost:5432/mydb \
  --username=ddl_utils_owner \
  --password=change-me \
  --changelog-file=ddl_utils/src/main/resources/db/changelog/db.changelog-master.xml \
  rollback-count --count=1
```

### Docker instead of a local CLI

The repository's `compose.yaml` runs the same changelog in the official
Liquibase image, which already has `lpm` on `PATH`. Use it as a template, or run
the CLI container directly (mounting the changelog read-only):

```sh
docker run --rm -it \
  --network host \
  -v "$PWD/ddl_utils/src/main/resources/db/changelog:/liquibase/changelog:ro" \
  liquibase/liquibase:5.0.4 \
  sh -c "lpm add postgresql --global && \
         liquibase --url=jdbc:postgresql://localhost:5432/mydb \
                   --username=ddl_utils_owner --password=change-me \
                   --changelog-file=changelog/db.changelog-master.xml update"
```

`--network host` reaches a database on the Docker host on Linux; on Docker
Desktop use `host.docker.internal` in the JDBC URL instead.

### Building it into your own changelog

If your application already uses Liquibase, vendor the changelog instead of
running a second process. Copy the
`ddl_utils/src/main/resources/db/changelog/` tree into your changelog directory (keeping the internal relative paths
intact) and include the master changelog
from yours:

```xml

<include file="db/changelog/db.changelog-master.xml"/>
```

Alternatively, keep the changelog in a jar on the Liquibase classpath:

```sh
liquibase --classpath=ddl_utils.jar \
  --changelog-file=db/changelog/db.changelog-master.xml update
```

## Usage

```sql
-- database-wide defaults (optional; there is a seeded row)
SELECT ddl_utils.set_database_lock_settings(
               i_ddl_lock_timeout => 250,
               i_sleep_time => 500,
               i_statement_duration => 30000
       );

-- override for one table
SELECT ddl_utils.set_table_lock_settings('public', 'orders', 250, 500, 30000);

-- add, rename, or drop a column; each wrapper resolves the settings itself
SELECT ddl_utils.add_column(
               i_schema_name => 'public',
               i_table_name => 'orders',
               i_column_name => 'note',
               i_column_type => 'text',
               i_nullable => true
       );
SELECT ddl_utils.rename_column('public', 'orders', 'note', 'comment');
SELECT ddl_utils.drop_column('public', 'orders', 'comment');

-- metadata-only column attribute changes
SELECT ddl_utils.set_column_default('public', 'orders', 'note', '''pending''');
SELECT ddl_utils.drop_column_default('public', 'orders', 'note');
SELECT ddl_utils.drop_not_null('public', 'orders', 'note');

-- add a constraint without a long lock: NOT VALID now, validate separately.
-- VALIDATE takes only SHARE UPDATE EXCLUSIVE, so it has no lock-aware wrapper.
SELECT ddl_utils.add_check_constraint('public', 'orders', 'orders_note_present', 'note IS NOT NULL');
SELECT ddl_utils_lib.validate_constraint('public', 'orders', 'orders_note_present', 250, 500, 30000);

-- attach a unique index built concurrently as a primary/unique constraint
--   CREATE UNIQUE INDEX CONCURRENTLY orders_id_idx ON public.orders (id);
SELECT ddl_utils.add_primary_key_using_index('public', 'orders', 'orders_pkey', 'orders_id_idx');

-- or call the explicit-settings helpers in ddl_utils_lib
SELECT ddl_utils_lib.add_column('public', 'orders', 'note', 'text', true, NULL, 250, 500, 30000);
```

`ddl_utils_lib.alter_table` is the internal runner the structured helpers build
on; prefer those so the SQL fragment is assembled from validated identifiers.

All routines run inside the caller's transaction and never commit; `lock_timeout`
is set with `SET LOCAL` semantics and restored afterwards. Callers are expected
to hold the privileges they would need to run the SQL directly — the helpers
are a convenience for lock handling, not a privilege boundary.

For tests, jOOQ classes are generated from the migrated schema into
`target/generated-sources/jooq`.

## Requirements

- Java 25
- A running Docker daemon (`./mvnw verify` uses Testcontainers for jOOQ code
  generation and the integration tests)
- The Maven wrapper (`./mvnw`); do not rely on a system Maven

## Build and test

Build the custom PostgreSQL image once, then run the build:

```sh
scripts/build-postgres-image.sh postgres:17-alpine   # -> ddl-utils-postgres:17-alpine
./mvnw clean verify
```

One module, or a single test:

```sh
./mvnw -pl ddl_utils -am verify
./mvnw -pl ddl_utils -am test -Dtest=TableLockSettingsTest -Dsurefire.failIfNoSpecifiedTests=false
```

SQLFluff lints the changelog `.sql` files during `verify` (create the repo
`.venv`, or point it at one with
`-Dsqlfluff.executable=$PWD/.venv/bin/sqlfluff`); skip it with `-Dskip.sqlfluff`.
The Liquibase changelog linter from `liquibase-validation` also runs during
`verify`: it applies rules that need both the SQL and the changeset attributes
(for example a statement PostgreSQL forbids in a transaction must be in a
`runInTransaction="false"` changeset), configured in
`ddl_utils/.liquibase-linter.yml`, with accepted findings listed in
`ddl_utils/.liquibase-linter-whitelist.yml` (an unaccepted finding and a stale
whitelist entry both fail the build); skip it with
`-Dskip.liquibase-linter`. The `Changelog linter` CI workflow additionally
uploads the linter's SARIF output to GitHub code scanning, so findings show
inline on pull requests.
`PlpgsqlCheckTest` also runs `plpgsql_check` over every routine and fails on any
finding not accepted in `plpgsql-check-whitelist.yml`, using the shared
`PlpgsqlCheck` helper from `liquibase-validation`. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the full workflow.

## Local development database

```sh
scripts/build-postgres-image.sh postgres:17-alpine   # once
scripts/start-local-db.sh                            # up -d --wait
```

This starts a `ddl-utils-postgres:17-alpine` container plus a one-shot Liquibase
service that applies the changelog as the `ddl_utils_owner` role. Connect with:

```sh
psql -h localhost -p 5432 -U postgres -d ddl_utils   # password: postgres
```

The port is bound to `127.0.0.1` only, and the credentials are set in
`compose.yaml`. Data lives in the `ddl_utils_pgdata` volume: `scripts/stop-local-db.sh`
keeps it, while `scripts/refresh-local-db.sh` wipes it and re-runs the
migrations.

## Custom PostgreSQL image

The build and local dev database use a custom image (`ddl-utils-postgres:<ver>-alpine`) built from the official
`postgres:<ver>-alpine` image. It bakes in a roles init script (`docker/postgres/roles.sql`) that creates the
application roles before
Liquibase runs:

- `ddl_utils_owner` — owns the schemas and objects; Liquibase connects as this
  role (never as `postgres`).
- `ddl_utils_caller` — the role privileges are granted to (e.g. `SELECT` on the
  lock-settings tables and `EXECUTE` on the routines).
- `ddl_utils_test` — granted `ddl_utils_caller`; used by the integration tests.

The image also compiles the [`plpgsql_check`](https://github.com/okbob/plpgsql_check)
extension from source (pinned and checksum-verified), so it is available in dev
databases for static analysis of the routines:

```sql
SELECT plpgsql_check_function('ddl_utils.get_lock_settings(text, text)'::regprocedure);
```

The init script only runs on first initialization, so an existing data volume
keeps its roles and installed extensions as-is (it will not get
`plpgsql_check`); recreate the volume (`scripts/refresh-local-db.sh`) to pick up
a new image.

## Running against a different PostgreSQL version

The PostgreSQL version the build runs against is a single source of truth
controlled by the `postgres.version` Maven property (`17-alpine` by default). It
is used for jOOQ code generation and the integration tests, and it drives the
custom image tag:

```sh
# Build the custom image for that version, then run the build/tests
scripts/build-postgres-image.sh postgres:16-alpine
./mvnw verify -Dpostgres.version=16-alpine

# Local dev database (Docker Compose) takes the image tag directly
POSTGRES_IMAGE=ddl-utils-postgres:16-alpine docker compose up -d
```

The `postgres.image` property is derived as
`ddl-utils-postgres:${postgres.version}` and must be the custom image; the build
rejects a stock `postgres` image at `validate` (it lacks the application roles
and `plpgsql_check`). CI builds the custom image and runs the full build against
PostgreSQL 16, 17 and 18 (see `.github/workflows/maven.yml`).

## Changelog validation dependency

Changelog validation (changeset and SQL naming, orphaned SQL files) lives in the
[`liquibase_validation`](https://github.com/eyupmiduck/liquibase_validation)
project and is consumed as the test-scoped `io.github.eyupmiduck:liquibase-validation`
artifact from GitHub Packages. GitHub Packages requires authentication even for
public packages, so a classic personal access token with the `read:packages`
scope must be configured under the `github` server id in `~/.m2/settings.xml`
for local builds. See that project's README for the settings snippet and the CI
access requirements.

## Open source projects

ddl_utils is built on and maintained with these open source projects:

- [PostgreSQL](https://www.postgresql.org/) — the database these helpers target
  and exercise.
- [Liquibase](https://www.liquibase.org/) — applies and versions the database
  schema changes.
- [jOOQ](https://www.jooq.org/) — generates the type-safe Java classes used by
  the tests and consumers.
- [Testcontainers](https://testcontainers.com/) — runs the throwaway PostgreSQL
  container for jOOQ code generation and the integration tests.
- [JUnit 5](https://junit.org/) — the test framework.
- [plpgsql_check](https://github.com/okbob/plpgsql_check) — statically analyses
  the PL/pgSQL routines.
- [SQLFluff](https://sqlfluff.com/) — lints the changelog SQL files.
- [liquibase-validation](https://github.com/eyupmiduck/liquibase_validation) —
  the changelog linter and the `plpgsql_check`/audit-column test helpers.
- [CodeQL](https://codeql.github.com/) — static analysis of the Java code in CI.
- [Dependabot](https://github.com/dependabot) — keeps the Maven and GitHub
  Actions dependencies up to date.
- [OpenJDK](https://openjdk.org/) — provides the Java runtime (Java 25) the
  project targets.
- [Apache Maven](https://maven.apache.org/) — builds the project and manages
  dependencies (through the Maven Wrapper).
- [OpenCodeReview](https://open-codereview.ai/) — runs the AI code review on
  pull requests.

## More

- [CONTRIBUTING.md](CONTRIBUTING.md) — build, test, and changelog conventions
- [AGENTS.md](AGENTS.md) — repository layout and development principles
- [SECURITY.md](SECURITY.md) — how to report a security issue

# AGENTS.md

## Project

This is a Java project for PostgreSQL database tooling.

Primary technologies:

- Java 25
- Maven
- PostgreSQL
- jOOQ
- Liquibase
- JUnit 5
- Testcontainers

## Repo state

Early stage: Maven multi-module stub exists, but no real source code yet.
CI: GitHub Actions (`.github/workflows/maven.yml`) runs `./mvnw clean verify`
on pull requests to `main`.

- Root `pom.xml`: parent POM (`ddl-utils-parent`); all dependency and plugin
  versions are pinned here in `dependencyManagement` / `pluginManagement`.
- `ddl_utils/`: the main module; base package
  `io.github.eyupmiduck.ddlutils`.
    - Liquibase changelogs: `src/main/resources/db/changelog/`
      (`db.changelog-master.xml` includes `changes/changes.xml`, which holds
      the schema/table changesets; forward SQL lives in
      `changes/sql_changes/`, rollback SQL in `changes/rollback/`).
      `changes/functions.xml` and `changes/procedures.xml` each hold one
      `NNN-`prefixed changeset per routine (one `createProcedure` plus its
      rollback), with one file per routine under `changes/functions/<schema>/`
      and `changes/procedures/<schema>/` (rollback bodies under
      `changes/functions-rollback/<schema>/`).       `changes/functions/README.md`
      lists each routine's signature and purpose. The `ddl_utils`
      schema holds the lock-settings tables/accessors, the shared domains, and
      the lock-aware DDL wrappers (columns, constraints and tables) that resolve
      their settings through `get_lock_settings`; `ddl_utils_lib` holds the
      generic DDL helpers of the same names that take the settings explicitly,
      with `alter_table` as the internal runner. The helpers cover only
      metadata-only `ALTER TABLE` operations (brief `ACCESS EXCLUSIVE`, or a
      weaker `SHARE UPDATE EXCLUSIVE`/`SHARE ROW EXCLUSIVE` lock; no scan or
      rewrite). A function must make a single `ALTER TABLE` call because it
      cannot commit mid-call, so multi-step sequences (for example
      check-validate-set NOT NULL) are not implemented as one helper.
    - jOOQ classes are generated at build time into
      `target/generated-sources/jooq` by
      `testcontainers-jooq-codegen-maven-plugin`, which starts a real
      PostgreSQL container and applies the Liquibase changelog. **Docker must be
      running for `./mvnw verify`.** Plugin 0.0.4 is old: the module POM
      overrides its bundled Testcontainers and jOOQ — keep those overrides.
- `docker_java_config/`: jar containing only `docker-java.properties`
  (`api.version=1.44`). Testcontainers <= 1.21.3 shades docker-java pinned to
  Docker API 1.32, but Docker 29 requires >= 1.40. This module puts the pin on
  the codegen plugin realm and the test classpath. Remove once Testcontainers
  supports Docker 29+ natively.

## Useful commands

- Everything: `./mvnw verify`
- One module: `./mvnw -pl ddl_utils -am verify` (`-am` is required — reactor
  deps are not installed)
- One test: `./mvnw -pl ddl_utils -am test -Dtest=TableLockSettingsTest`
- Lint SQL only: `.venv/bin/sqlfluff lint ddl_utils/src/main/resources/db/changelog`
- Auto-fix SQL style: `scripts/sqlfluff-fix.sh` (uses the repo's `.venv`)

## Development principles

- Prefer simple, explicit Java over unnecessary abstractions.
- Use modern Java 25 features where they improve readability.
- Keep methods small and focused.
- Avoid adding dependencies unless there is a clear benefit.
- Do not introduce frameworks unless specifically requested.
- Follow the existing project structure and conventions.

## Maven

- Always use the Maven Wrapper:
  `./mvnw`
- Do not assume a globally installed Maven version.
- Changes should pass:
  `./mvnw verify`

## PostgreSQL

- Target PostgreSQL unless explicitly told otherwise.
- Prefer PostgreSQL-native solutions over database-portable abstractions.
- SQL must be safe for production-sized databases.
- Consider locking, transaction boundaries, concurrency, and failure recovery.
- Avoid operations that unnecessarily require long ACCESS EXCLUSIVE locks.
- Do not assume small tables.

## PL/pgSQL

- **Prefer stored functions over stored procedures.** Functions cannot
  `COMMIT`/`ROLLBACK` or manage transactions, so transaction control can never
  leak into code that must run inside the caller's transaction.
- Prefix input arguments with `i_`, output arguments with `o_`, and local
  variables with `l_`. Use `snake_case` for object names, arguments, and
  variables.
- When one routine calls another, pass arguments **by name**
  (`ddl_utils_lib.alter_table(i_schema_name => ..., ...)`) rather than
  positionally, so reordering or inserting a parameter cannot silently rebind
  values.
- One routine per `.sql` file, grouped by the schema that owns it: functions in
  `ddl_utils/src/main/resources/db/changelog/changes/functions/<schema>/<name>.sql`,
  procedures in `.../changes/procedures/<schema>/<name>.sql`, named `snake_case`
  without an `NNN-` prefix. `changes/functions.xml` and
  `changes/procedures.xml` each contain one `NNN-`prefixed changeset per
  routine (one `createProcedure` plus its rollback); both are included from
  `changes/changes.xml`.
- Load a routine with the `createProcedure` change type and an external body:
  `<createProcedure path="functions/<schema>/<name>.sql" relativeToChangelogFile="true"/>`.
  Liquibase has no `createFunction` change type, so functions use
  `createProcedure` too; the `path` attribute keeps SQL out of the XML.
- `CREATE OR REPLACE` only replaces a routine with an identical signature.
  Changing `RETURNS` or a parameter name aborts the deploy, and changing a
  parameter type leaves the old overload behind. When a signature changes, add
  an explicit `DROP FUNCTION IF EXISTS <old signature>;` (for example another
  `sqlFile` in the same changeset) so the deprecated signature is removed.
- Type routine arguments with the `ddl_utils` domains (for example
  `non_null_text`, `non_negative_integer`) so null or invalid inputs fail
  fast with a check-constraint violation.
- Use `SECURITY INVOKER` (the default). A routine must never require callers to
  hold privileges beyond what they would need to run its SQL directly: if a
  caller could run the statement itself, calling the routine must just work.
  Use `SECURITY DEFINER` only when a caller genuinely must perform an operation
  it lacks privileges for, and then pin a safe `search_path` and grant
  `EXECUTE` explicitly (revoking it from `PUBLIC`).
- Never build dynamic SQL by concatenating values. Quote identifiers with
  `format('... %I ...', ...)` and literals with `%L`, and reject input that
  cannot be safely parameterized (for example an `ALTER TABLE` fragment with
  multiple statements or comments).
- Schema-qualify objects or set `search_path` explicitly so a routine behaves
  the same regardless of the caller's `search_path`.
- Routines run inside the caller's transaction: use `SET LOCAL` for
  transaction-scoped settings and never assume state survives a rollback.

## Liquibase

- Database schema changes must be implemented through Liquibase.
- Changesets should be small and focused.
- **Do not embed SQL in XML.** Put SQL in a `.sql` file and reference it with
  `<sqlFile path="..." relativeToChangelogFile="true"/>` (also for
  `<rollback>`). Add each changeset as a `<changeSet id="NNN-description">`
  entry in `ddl_utils/src/main/resources/db/changelog/changes/changes.xml`,
  with forward SQL in `changes/sql_changes/NNN-description.sql` and rollback
  SQL in `changes/rollback/NNN-description-rollback.sql`. Stored routines are
  the exception: they use the `createProcedure` change type with a `path` to a
  per-routine `.sql` file (see the PL/pgSQL section).
- Prefer changes that are safe to deploy against a live database.
- Consider rollback and idempotency where appropriate.
- Do not modify an already-deployed changeset unless explicitly instructed.
- SQLFluff (`.sqlfluff`, dialect `postgres`) lints the changelog `.sql` files
  during `verify` via `exec-maven-plugin`. Requires `sqlfluff` on PATH (use
  the repo's `.venv`); skip with `-Dskip.sqlfluff`.

## jOOQ

- Prefer jOOQ's type-safe DSL over constructing SQL strings manually.
- Use generated jOOQ classes where available.
- Do not duplicate database schema definitions in Java.
- Use plain SQL when PostgreSQL-specific functionality cannot be expressed
  clearly with the jOOQ DSL.

## Testing

- Use JUnit 5.
- Integration tests must use Testcontainers where a real PostgreSQL database
  is required.
- Do not replace PostgreSQL integration tests with H2 or another database.
  This also applies to tooling: no H2 anywhere, including jOOQ code
  generation (do not use jOOQ's offline `LiquibaseDatabase`/H2 simulation).
- Tests should be deterministic and independent.
- Database tests must extend `PostgresTestBase` (in `ddl_utils` test
  sources). It shares one PostgreSQL container, applies the Liquibase
  changelog once to a template database, and gives each test class a private
  database cloned with `CREATE DATABASE ... TEMPLATE ...` (fast, isolated
  data). Use the inherited `dsl` (jOOQ); do not run Liquibase or start
  containers in individual tests.
- Every test class and test method must have Javadoc describing the behavior
  it verifies.
- Prefer testing observable behavior rather than implementation details.
- Add regression tests when fixing bugs.
- The routines are statically analysed with the `plpgsql_check` extension (`PlpgsqlCheckTest`). It is compiled into the
  custom image, created in the
  template database, and available in dev databases via
  `docker/postgres/roles.sql`; keep the routines free of its warnings.

## Before completing a change

1. Review the diff.
2. Remove unnecessary code and imports.
3. Check for accidental API or schema changes.
4. Run relevant tests.
5. Run `./mvnw verify`.
6. Report any tests that could not be run.

## Git

- Never commit directly to `main`.
- Work on a feature branch.
- Keep commits focused.
- Do not commit generated build output, secrets, credentials, or local IDE files.

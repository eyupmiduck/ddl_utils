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
    (`db.changelog-master.xml` includes one file per changeset from `changes/`).
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
- One test: `./mvnw -pl ddl_utils -am test -Dtest=ExampleTableTest`
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

## Liquibase

- Database schema changes must be implemented through Liquibase.
- Changesets should be small and focused.
- **Do not embed SQL in XML.** Put SQL in a `.sql` file next to the changeset
  and reference it with `<sqlFile path="..." relativeToChangelogFile="true"/>`
  (also for `<rollback>`). One changeset per XML file in
  `ddl_utils/src/main/resources/db/changelog/changes/`, named
  `NNN-description.xml` with matching `NNN-description.sql` /
  `NNN-description-rollback.sql`.
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

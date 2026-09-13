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

Early-stage skeleton: **no source code, no `pom.xml`, no tests, no CI yet** — only the toolchain is pinned. `./mvnw verify` will fail until a `pom.xml` exists. When adding the first code, establish the project structure as part of the change.

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
- Prefer changes that are safe to deploy against a live database.
- Consider rollback and idempotency where appropriate.
- Do not modify an already-deployed changeset unless explicitly instructed.

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
- Tests should be deterministic and independent.
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

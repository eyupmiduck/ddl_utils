package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ddl_utils' PostgreSQL test base: the shared
 * {@link io.github.eyupmiduck.changelogvalidator.testing.PostgresTestBase}
 * configured for the ddl_utils roles, schemas and changelog, plus the
 * ddl_utils-specific lock, constraint and column helpers.
 *
 * <p>The shared base starts one container, migrates a template database from
 * the production changelog and clones a private database per test class as the
 * {@code ddl_utils_test} role. The custom image's init script creates the
 * roles and installs {@code plpgsql_check}.
 */
abstract class PostgresTestBase extends io.github.eyupmiduck.changelogvalidator.testing.PostgresTestBase {

    /**
     * The per-attempt lock timeout in ms used by tests that call the
     * explicit-settings {@code ddl_utils_lib} helpers.
     */
    protected static final int DDL_LOCK_TIMEOUT = 1000;

    /**
     * The retry sleep in ms used by tests that call the explicit-settings
     * helpers.
     */
    protected static final int SLEEP_TIME = 10;

    /**
     * The statement budget in ms used by tests that call the explicit-settings
     * helpers.
     */
    protected static final int STATEMENT_DURATION = 5000;

    /**
     * The schema Liquibase keeps its tracking tables in, so they stay out of the
     * application schemas.
     */
    static final String LIQUIBASE_SCHEMA = "liquibase";
    static final String DATABASE_CHANGELOG_TABLE = "ddl_utils_databasechangelog";
    static final String DATABASE_CHANGELOG_LOCK_TABLE = "ddl_utils_databasechangeloglock";

    @Override
    protected String defaultPostgresImage() {
        return "ddl-utils-postgres:17-alpine";
    }

    @Override
    protected String databaseName() {
        return "ddl_utils";
    }

    @Override
    protected String ownerUser() {
        return "ddl_utils_owner";
    }

    @Override
    protected String ownerPassword() {
        return "ddl_utils_owner";
    }

    @Override
    protected String testUser() {
        return "ddl_utils_test";
    }

    @Override
    protected String testPassword() {
        return "ddl_utils_test";
    }

    @Override
    protected String databaseChangeLogTableName() {
        return DATABASE_CHANGELOG_TABLE;
    }

    @Override
    protected String databaseChangeLogLockTableName() {
        return DATABASE_CHANGELOG_LOCK_TABLE;
    }

    @Override
    protected void installExtensions(String templateDatabase) throws SQLException {
        try (Connection admin = openAdminConnection(templateDatabase);
             Statement statement = admin.createStatement()) {
            // Install the static-analysis extension (compiled into the custom
            // image) so every cloned test database has it.
            statement.execute("CREATE EXTENSION IF NOT EXISTS plpgsql_check");
        }
    }

    /**
     * Returns the {@code pg_locks.mode} spelling of a lock mode written the way
     * {@code LOCK TABLE} expects it, for example {@code ACCESS SHARE} to
     * {@code AccessShareLock}.
     *
     * @param mode the lock mode as written in SQL
     * @return the lock mode as reported by {@code pg_locks}
     */
    private static String lockModeName(String mode) {
        StringBuilder name = new StringBuilder();
        for (String word : mode.trim().split("\\s+")) {
            name.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase());
        }
        return name.append("Lock").toString();
    }

    /**
     * Asserts that a lock-settings row holds the expected values.
     *
     * @param row               the settings row
     * @param ddlLockTimeout    the expected {@code ddl_lock_timeout} in ms
     * @param sleepTime         the expected {@code sleep_time} in ms
     * @param statementDuration the expected {@code statement_duration} in ms
     */
    protected static void assertLockSettings(Record row, int ddlLockTimeout, int sleepTime, int statementDuration) {
        assertEquals(ddlLockTimeout, row.get("ddl_lock_timeout", Integer.class));
        assertEquals(sleepTime, row.get("sleep_time", Integer.class));
        assertEquals(statementDuration, row.get("statement_duration", Integer.class));
    }

    /**
     * Holds an ACCESS SHARE lock on a table on a second connection, which
     * conflicts with the ACCESS EXCLUSIVE lock an {@code ALTER TABLE} needs.
     * The caller owns the connection and must close it to release the lock.
     *
     * @param connection a connection to this test class's private database
     * @param table      the table to lock
     * @throws SQLException if the lock cannot be taken
     */
    protected void holdAccessShareLock(Connection connection, String table) throws SQLException {
        holdTableLock(connection, table, "ACCESS SHARE");
    }

    /**
     * Holds a table lock in the given mode on a second connection, so a test
     * can make a routine wait for it. The caller owns the connection and must
     * close it to release the lock.
     *
     * @param connection a connection to this test class's private database
     * @param table      the table to lock
     * @param mode       the PostgreSQL lock mode, for example {@code ACCESS SHARE}
     * @throws SQLException if the lock cannot be taken
     */
    protected void holdTableLock(Connection connection, String table, String mode) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("LOCK TABLE " + table + " IN " + mode + " MODE");
        }
    }

    /**
     * Waits until a competing session holds an ACCESS SHARE lock on the table,
     * so a test can be sure the lock is in place before calling a routine that
     * must wait for it.
     *
     * @param table the table to check
     * @throws InterruptedException if interrupted while waiting
     */
    protected void awaitAccessShareLockHeld(String table) throws InterruptedException {
        awaitTableLockHeld(table, "AccessShareLock");
    }

    /**
     * Waits until a competing session holds a lock in the given mode on the
     * table, so a test can be sure the lock is in place before calling a
     * routine that must wait for it.
     *
     * @param table the table to check
     * @param mode  the PostgreSQL lock mode as reported by {@code pg_locks},
     *              for example {@code AccessExclusiveLock}
     * @throws InterruptedException if interrupted while waiting
     */
    protected void awaitTableLockHeld(String table, String mode) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Object held = dsl.fetchValue(
                    """
                            SELECT EXISTS (
                                SELECT 1
                                FROM pg_locks l
                                JOIN pg_class c ON c.oid = l.relation
                                JOIN pg_namespace n ON n.oid = c.relnamespace
                                WHERE c.relname = ?
                                    AND n.nspname = current_schema()
                                    AND l.mode = ?
                                    AND l.granted
                            )
                            """,
                    table, mode);
            if (Boolean.TRUE.equals(held)) {
                return;
            }
            Thread.sleep(50);
        }
        fail("the competing session did not acquire its " + mode + " lock on " + table);
    }

    /**
     * Rolls back a connection after a delay on a dedicated daemon thread, so a
     * test can release a held lock while the test thread is blocked in a call.
     * The returned future completes once the rollback has run, or fails with
     * the rollback error.
     *
     * @param connection  the connection to roll back
     * @param delayMillis how long to hold before rolling back
     * @return a future that completes when the rollback has run
     */
    protected CompletableFuture<Void> rollbackAfter(Connection connection, long delayMillis) {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
                connection.rollback();
                completed.complete(null);
            } catch (Throwable e) {
                completed.completeExceptionally(e);
            }
        }, "lock-holder-rollback");
        thread.setDaemon(true);
        thread.start();
        return completed;
    }

    /**
     * Creates a table owned by the test role, so SECURITY INVOKER routines that
     * require ownership can operate on it.
     *
     * @param table   the table name
     * @param columns the column definitions, without the surrounding
     *                parentheses
     */
    protected void createTestTable(String table, String columns) {
        dsl.execute("CREATE TABLE " + table + " (" + columns + ")");
    }

    /**
     * Drops a table created by {@link #createTestTable}, if it exists.
     *
     * @param table the table name
     */
    protected void dropTestTable(String table) {
        dsl.execute("DROP TABLE IF EXISTS " + table);
    }

    /**
     * Returns the deterministic name of the temporary CHECK constraint the
     * {@code ddl_utils.ensure_not_null} procedure uses for a column, so a test
     * can reproduce the catalog state left by an interrupted run.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the temporary constraint name
     */
    protected String notNullCheckConstraintName(String schema, String table, String column) {
        String digest = dsl.fetchOne("SELECT substr(md5(? || '.' || ? || '.' || ?), 1, 8)",
                schema, table, column).get(0, String.class);
        // Mirror the procedure: replace non-ASCII characters so the prefix is
        // measured in bytes before it is truncated to 45 characters.
        String prefix = (table + "_" + column + "_not_null").replaceAll("[^A-Za-z0-9_]", "_");
        if (prefix.length() > 45) {
            prefix = prefix.substring(0, 45);
        }
        return prefix + "_" + digest;
    }

    /**
     * Reproduces the catalog state left when {@code ddl_utils.ensure_not_null}
     * committed its first step but the call ended before validation: the
     * temporary CHECK constraint exists but is {@code NOT VALID}.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     */
    protected void simulateNotNullCheckAdded(String schema, String table, String column) {
        dsl.execute("ALTER TABLE " + schema + "." + table
                + " ADD CONSTRAINT " + notNullCheckConstraintName(schema, table, column)
                + " CHECK (" + column + " IS NOT NULL) NOT VALID");
    }

    /**
     * Reproduces the catalog state left when {@code ddl_utils.ensure_not_null}
     * committed its first two steps but the call ended before {@code SET NOT
     * NULL}: the temporary CHECK constraint exists and is valid.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     */
    protected void simulateNotNullCheckValidated(String schema, String table, String column) {
        simulateNotNullCheckAdded(schema, table, column);
        dsl.execute("ALTER TABLE " + schema + "." + table
                + " VALIDATE CONSTRAINT " + notNullCheckConstraintName(schema, table, column));
    }

    /**
     * Returns a column's {@code pg_attribute} row, failing when the column does
     * not exist. Used for attributes that {@code information_schema.columns}
     * does not expose ({@code attidentity}, {@code attstorage},
     * {@code attcompression}, {@code attgenerated}).
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the column's pg_attribute row
     */
    private Record pgAttribute(String schema, String table, String column) {
        Record record = dsl.fetchOne(
                """
                        SELECT a.attidentity::text AS attidentity,
                               a.attstorage::text AS attstorage,
                               a.attcompression::text AS attcompression,
                               (a.attgenerated <> '') AS attgenerated
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                schema, table, column);
        assertNotNull(record, () -> "column not found: " + schema + "." + table + "." + column);
        return record;
    }

    /**
     * Returns a column's identity code: {@code 'a'} for ALWAYS, {@code 'd'} for
     * BY DEFAULT, {@code ''} for no identity.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the identity code
     */
    protected String columnIdentity(String schema, String table, String column) {
        return pgAttribute(schema, table, column).get("attidentity", String.class);
    }

    /**
     * Returns a column's storage mode name ({@code plain}, {@code external},
     * {@code main} or {@code extended}).
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the storage mode name
     */
    protected String columnStorage(String schema, String table, String column) {
        // attstorage is a single code: p=plain, e=external, m=main, x=extended.
        String code = pgAttribute(schema, table, column).get("attstorage", String.class);
        return switch (code) {
            case "p" -> "plain";
            case "e" -> "external";
            case "m" -> "main";
            case "x" -> "extended";
            default -> code;
        };
    }

    /**
     * Returns a column's compression method name ({@code pglz}, {@code lz4} or
     * {@code default}).
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the compression method name
     */
    protected String columnCompression(String schema, String table, String column) {
        // attcompression is a single code: p=pglz, l=lz4, empty=default.
        String code = pgAttribute(schema, table, column).get("attcompression", String.class);
        return switch (code) {
            case "p" -> "pglz";
            case "l" -> "lz4";
            case "" -> "default";
            default -> code;
        };
    }

    /**
     * Returns whether a column is generated.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return {@code true} when the column is generated
     */
    protected boolean isGenerated(String schema, String table, String column) {
        return Boolean.TRUE.equals(pgAttribute(schema, table, column).get("attgenerated", Boolean.class));
    }

    /**
     * Returns whether the named constraint on a table is validated, failing when
     * no such constraint exists on that table. The lookup is scoped to the
     * {@code (schema, table, name)} triple because constraint names are only
     * unique per table.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param name   the constraint name
     * @return {@code true} when the constraint is validated
     */
    protected boolean constraintValidated(String schema, String table, String name) {
        Record record = dsl.fetchOne(
                """
                        SELECT convalidated
                        FROM pg_constraint
                        WHERE conname = ?
                            AND conrelid = (SELECT oid FROM pg_class WHERE relname = ?
                                              AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?))
                        """,
                name, table, schema);
        assertNotNull(record, () -> "constraint not found: " + schema + "." + table + "." + name);
        return Boolean.TRUE.equals(record.get("convalidated", Boolean.class));
    }

    /**
     * Asserts that a column is nullable.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     */
    protected void assertNullable(String schema, String table, String column) {
        assertEquals("YES", columnAttribute(schema, table, column, "is_nullable"),
                () -> column + " should be nullable");
    }

    /**
     * Asserts that a column is not nullable.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     */
    protected void assertNotNullable(String schema, String table, String column) {
        assertEquals("NO", columnAttribute(schema, table, column, "is_nullable"),
                () -> column + " should not be nullable");
    }

    /**
     * Asserts that a column has a default expression containing the given text.
     *
     * @param schema            the table schema
     * @param table             the table name
     * @param column            the column name
     * @param expectedSubstring a substring the default expression must contain
     */
    protected void assertColumnDefault(String schema, String table, String column, String expectedSubstring) {
        String defaultExpression = columnAttribute(schema, table, column, "column_default");
        assertNotNull(defaultExpression, () -> column + " should have a default");
        assertTrue(defaultExpression.contains(expectedSubstring),
                () -> "default " + defaultExpression + " should contain " + expectedSubstring);
    }

    /**
     * Asserts that a column has no default expression.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     */
    protected void assertNoColumnDefault(String schema, String table, String column) {
        assertNull(columnAttribute(schema, table, column, "column_default"),
                () -> column + " should have no default");
    }

    /**
     * Asserts that the named constraint on a table is validated.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param name   the constraint name
     */
    protected void assertValidated(String schema, String table, String name) {
        assertTrue(constraintValidated(schema, table, name), () -> name + " should be validated");
    }

    /**
     * Asserts that the named constraint on a table is not validated.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param name   the constraint name
     */
    protected void assertNotValidated(String schema, String table, String name) {
        assertFalse(constraintValidated(schema, table, name), () -> name + " should not be validated");
    }

    /**
     * Returns whether the named constraint exists on a table.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param name   the constraint name
     * @return {@code true} when the constraint exists on that table
     */
    protected boolean constraintExists(String schema, String table, String name) {
        return Boolean.TRUE.equals(dsl.fetchValue(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = ?
                                AND conrelid = (SELECT oid FROM pg_class WHERE relname = ?
                                                  AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?))
                        )
                        """,
                name, table, schema));
    }

    /**
     * Returns whether any constraint with the given name exists in a schema.
     * Constraint names are unique per table, not per schema, so this matches a
     * name on any table in the schema.
     *
     * @param schema the schema name
     * @param name   the constraint name
     * @return {@code true} when a constraint with that name exists in the schema
     */
    protected boolean constraintExists(String schema, String name) {
        return Boolean.TRUE.equals(dsl.fetchValue(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = ?
                                AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        )
                        """,
                name, schema));
    }

    /**
     * Returns the {@code contype} of the named constraint in a schema
     * ({@code p} primary key, {@code u} unique, {@code f} foreign key,
     * {@code c} check).
     *
     * @param schema the schema name
     * @param name   the constraint name
     * @return the constraint type code
     */
    protected String constraintType(String schema, String name) {
        return dsl.fetchOne(
                """
                        SELECT contype::text FROM pg_constraint
                        WHERE conname = ? AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        """,
                name, schema).get(0, String.class);
    }

    /**
     * Returns the number of temporary NOT NULL proof CHECK constraints for a
     * column, or {@code -1} when the count cannot be read. Only CHECK
     * constraints count: PostgreSQL 18+ also records the column's NOT NULL as a
     * {@code pg_constraint} row.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the number of matching constraints, or {@code -1}
     */
    protected int notNullProofConstraintCount(String schema, String table, String column) {
        Integer count = dsl.fetchOne(
                """
                        SELECT count(*)::int
                        FROM pg_constraint
                        WHERE conrelid = (SELECT oid FROM pg_class WHERE relname = ?
                                            AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?))
                            AND contype = 'c'
                            AND conname = ?
                        """,
                table, schema, notNullCheckConstraintName(schema, table, column)).get(0, Integer.class);
        return count != null ? count : -1;
    }

    /**
     * Sets the table-level lock settings.
     *
     * @param schema      the table schema
     * @param table       the table name
     * @param lockTimeout the per-attempt lock timeout in ms
     * @param sleepTime   the retry sleep in ms
     * @param duration    the statement budget in ms
     */
    protected void setTableLockSettings(String schema, String table, Integer lockTimeout,
                                        Integer sleepTime, Integer duration) {
        dsl.fetchOne("SELECT ddl_utils.set_table_lock_settings(?, ?, ?, ?, ?)",
                schema, table, lockTimeout, sleepTime, duration);
    }

    /**
     * Clears the table-level lock settings.
     *
     * @param schema the table schema
     * @param table  the table name
     */
    protected void clearTableLockSettings(String schema, String table) {
        dsl.fetchOne("SELECT ddl_utils.clear_table_lock_settings(?, ?)", schema, table);
    }

    /**
     * Returns the table-level settings row, or {@code null} when there is none.
     *
     * @param schema the table schema
     * @param table  the table name
     * @return the settings row, or {@code null}
     */
    protected Record getTableLockSettings(String schema, String table) {
        return dsl.fetchOne(
                """
                        SELECT ddl_lock_timeout, sleep_time, statement_duration
                        FROM ddl_utils.get_table_lock_settings(?, ?)
                        """,
                schema, table);
    }

    /**
     * Sets the schema-level lock settings.
     *
     * @param schema      the schema name
     * @param lockTimeout the per-attempt lock timeout in ms
     * @param sleepTime   the retry sleep in ms
     * @param duration    the statement budget in ms
     */
    protected void setSchemaLockSettings(String schema, Integer lockTimeout,
                                         Integer sleepTime, Integer duration) {
        dsl.fetchOne("SELECT ddl_utils.set_schema_lock_settings(?, ?, ?, ?)",
                schema, lockTimeout, sleepTime, duration);
    }

    /**
     * Clears the schema-level lock settings.
     *
     * @param schema the schema name
     */
    protected void clearSchemaLockSettings(String schema) {
        dsl.fetchOne("SELECT ddl_utils.clear_schema_lock_settings(?)", schema);
    }

    /**
     * Returns the schema-level settings row, or {@code null} when there is none.
     *
     * @param schema the schema name
     * @return the settings row, or {@code null}
     */
    protected Record getSchemaLockSettings(String schema) {
        return dsl.fetchOne(
                """
                        SELECT ddl_lock_timeout, sleep_time, statement_duration
                        FROM ddl_utils.get_schema_lock_settings(?)
                        """,
                schema);
    }

    /**
     * Sets the database-level lock settings.
     *
     * @param lockTimeout the per-attempt lock timeout in ms
     * @param sleepTime   the retry sleep in ms
     * @param duration    the statement budget in ms
     */
    protected void setDatabaseLockSettings(Integer lockTimeout, Integer sleepTime, Integer duration) {
        dsl.fetchOne("SELECT ddl_utils.set_database_lock_settings(?, ?, ?)",
                lockTimeout, sleepTime, duration);
    }

    /**
     * Returns the database-level settings row.
     *
     * @return the settings row
     */
    protected Record getDatabaseLockSettings() {
        return dsl.fetchOne(
                """
                        SELECT ddl_lock_timeout, sleep_time, statement_duration
                        FROM ddl_utils.get_database_lock_settings()
                        """);
    }

    /**
     * Deletes the singleton database defaults row, for tests that exercise the
     * missing-row path. Use with {@link #restoreDatabaseDefaults}.
     */
    protected void deleteDatabaseDefaults() {
        try (Connection connection = openOwnerConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM ddl_utils.database_lock_settings WHERE id = 1");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to delete the database defaults", e);
        }
    }

    /**
     * Recreates the singleton database defaults row with the given values, for
     * tests that deleted it. A raw INSERT is needed because
     * {@code set_database_lock_settings} raises when the row is missing.
     *
     * @param ddlLockTimeout    the {@code ddl_lock_timeout} to restore
     * @param sleepTime         the {@code sleep_time} to restore
     * @param statementDuration the {@code statement_duration} to restore
     */
    protected void restoreDatabaseDefaults(int ddlLockTimeout, int sleepTime, int statementDuration) {
        try (Connection connection = openOwnerConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ddl_utils.database_lock_settings (
                         id, ddl_lock_timeout, sleep_time, statement_duration
                     )
                     VALUES (1, ?, ?, ?)
                     ON CONFLICT (id) DO UPDATE
                         SET ddl_lock_timeout   = EXCLUDED.ddl_lock_timeout,
                             sleep_time         = EXCLUDED.sleep_time,
                             statement_duration = EXCLUDED.statement_duration
                     """)) {
            statement.setInt(1, ddlLockTimeout);
            statement.setInt(2, sleepTime);
            statement.setInt(3, statementDuration);
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to restore the database defaults", e);
        }
    }

    /**
     * Returns the effective settings for a table (table, then schema, then
     * database).
     *
     * @param schema the table schema
     * @param table  the table name
     * @return the settings row
     */
    protected Record getLockSettings(String schema, String table) {
        return dsl.fetchOne(
                """
                        SELECT ddl_lock_timeout, sleep_time, statement_duration
                        FROM ddl_utils.get_lock_settings(?, ?)
                        """,
                schema, table);
    }

    /**
     * Runs {@code call} while another session holds an ACCESS SHARE lock on
     * {@code table}, and asserts it gives up with SQLSTATE {@code 55P03} within
     * {@code maxMillis}. The settings passed to the call must make the budget
     * short, so the bound distinguishes giving up from retrying for seconds.
     *
     * @param table     the locked table
     * @param maxMillis the maximum expected time to give up
     * @param call      the call expected to fail
     * @throws SQLException         if the competing connection cannot be opened
     * @throws InterruptedException if waiting for the lock is interrupted
     */
    protected void assertGivesUpWhileTableLocked(String table, long maxMillis, Executable call)
            throws SQLException, InterruptedException {
        assertGivesUpWhileTableLocked(table, "ACCESS SHARE", maxMillis, call);
    }

    /**
     * Runs {@code call} while another session holds a lock in the given mode on
     * {@code table}, and asserts it gives up with SQLSTATE {@code 55P03} within
     * {@code maxMillis}. The settings passed to the call must make the budget
     * short, so the bound distinguishes giving up from retrying for seconds.
     * Use {@code ACCESS EXCLUSIVE} for routines whose final statement takes only
     * SHARE UPDATE EXCLUSIVE, which an ACCESS SHARE lock does not block.
     *
     * @param table     the locked table
     * @param mode      the PostgreSQL lock mode, for example {@code ACCESS SHARE}
     * @param maxMillis the maximum expected time to give up
     * @param call      the call expected to fail
     * @throws SQLException         if the competing connection cannot be opened
     * @throws InterruptedException if waiting for the lock is interrupted
     */
    protected void assertGivesUpWhileTableLocked(String table, String mode, long maxMillis, Executable call)
            throws SQLException, InterruptedException {
        try (Connection other = openTestConnection()) {
            holdTableLock(other, table, mode);
            awaitTableLockHeld(table, lockModeName(mode));

            long startedAt = System.nanoTime();
            assertSqlState("55P03", call);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

            assertTrue(elapsedMillis < maxMillis,
                    () -> "the call did not give up within " + maxMillis + " ms; took " + elapsedMillis + " ms");
        }
    }

    /**
     * Runs {@code call} while another session holds an ACCESS SHARE lock on
     * {@code table}, releasing the lock after {@code releaseDelayMillis}.
     *
     * @param table              the locked table
     * @param releaseDelayMillis how long to hold the lock before releasing it
     * @param call               the call to run
     */
    protected void runWhileTableLocked(String table, long releaseDelayMillis, Runnable call) {
        try (Connection other = openTestConnection()) {
            holdAccessShareLock(other, table);
            awaitAccessShareLockHeld(table);

            CompletableFuture<Void> release = rollbackAfter(other, releaseDelayMillis);
            Throwable failure = null;
            try {
                call.run();
            } catch (Throwable e) {
                failure = e;
                throw e;
            } finally {
                // Do not let a rollback failure hide the primary failure.
                try {
                    release.join();
                } catch (CompletionException e) {
                    if (failure != null) {
                        failure.addSuppressed(e);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new IllegalStateException("failed while running under a table lock", e);
        }
    }
}

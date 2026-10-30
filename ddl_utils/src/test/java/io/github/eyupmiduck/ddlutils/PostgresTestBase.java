package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogTestSupport;
import org.jooq.Record;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ddl_utils' PostgreSQL test base: the shared
 * {@link io.github.eyupmiduck.changelogvalidator.testing.PostgresTestBase}
 * configured for the ddl_utils roles, schemas and changelog, plus the
 * ddl_utils lock-settings and {@code ensure_not_null} state helpers. The
 * generic lock, catalog and table helpers are inherited.
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

    /**
     * The container configuration, shared by this base and
     * {@link LiquibaseUpgradeTest} so the two cannot drift (the container's
     * template database is prepared once per JVM from the first class that
     * runs, so an inconsistent configuration would be order-dependent).
     */
    static final String POSTGRES_IMAGE = "ddl-utils-postgres:17-alpine";
    static final String DATABASE = "ddl_utils";
    static final String OWNER_USER = "ddl_utils_owner";
    static final String OWNER_PASSWORD = "ddl_utils_owner";
    static final String TEST_USER = "ddl_utils_test";
    static final String TEST_PASSWORD = "ddl_utils_test";

    /**
     * The one container and changelog configuration, returned by this class and
     * by {@link LiquibaseUpgradeTest} so the two cannot drift.
     */
    static final Configuration CONFIGURATION = new Configuration(
            DATABASE, OWNER_USER, OWNER_PASSWORD, TEST_USER, TEST_PASSWORD,
            POSTGRES_IMAGE, ChangelogTestSupport.MASTER_RESOURCE,
            DATABASE_CHANGELOG_TABLE, DATABASE_CHANGELOG_LOCK_TABLE, null,
            PostgresTestBase::installProjectExtensions);

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
     * Installs the static-analysis extension (compiled into the custom image)
     * so a database has it before the changelog runs. Shared with
     * {@link LiquibaseUpgradeTest}, whose scratch database needs the same
     * extension.
     *
     * @param admin a superuser connection to the target database
     * @throws SQLException if the extension cannot be installed
     */
    static void installProjectExtensions(Connection admin) throws SQLException {
        try (Statement statement = admin.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS plpgsql_check");
        }
    }

    @Override
    protected Configuration configuration() {
        return CONFIGURATION;
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
}

package io.github.eyupmiduck.ddlutils;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for tests that need a migrated PostgreSQL database.
 *
 * <p>A single PostgreSQL container is shared by all tests. On first use, the
 * roles are created by the custom image's init script and the Liquibase
 * changelog is applied once to a template database, connecting as the
 * {@code ddl_utils_owner} role (like a real deployment). Each test class then
 * gets its own private database, created cheaply with
 * {@code CREATE DATABASE ... TEMPLATE ...}, and connects to it as the
 * {@code ddl_utils_test} role, which is granted the {@code ddl_utils_caller}
 * role. The private database is dropped after the class finishes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PostgresTestBase {

    /**
     * The schema tests create their own tables in; the test role has CREATE on
     * it.
     */
    protected static final String PUBLIC_SCHEMA = "public";
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String TEMPLATE_DATABASE = "ddl_utils_template";
    private static final String OWNER_USER = "ddl_utils_owner";
    private static final String OWNER_PASSWORD = "ddl_utils_owner";
    private static final String TEST_USER = "ddl_utils_test";
    private static final String TEST_PASSWORD = "ddl_utils_test";
    /**
     * The PostgreSQL image to run, matching the one used for jOOQ codegen.
     * Set by surefire from the {@code postgres.image} Maven property. The
     * custom image has the application roles baked in.
     */
    private static final String POSTGRES_IMAGE =
            System.getProperty("postgres.image", "ddl-utils-postgres:17-alpine");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE)
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ddl_utils");

    static {
        try {
            POSTGRES.start();
            prepareTemplateDatabase();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start the PostgreSQL test container", e);
        }
    }

    /**
     * jOOQ context connected to this test class's private database.
     */
    protected DSLContext dsl;
    private String databaseName;
    private Connection connection;

    private static void prepareTemplateDatabase() {
        try {
            try (Connection admin = openConnection(POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = admin.createStatement()) {
                // Tolerate a template left behind by an interrupted earlier run
                // in the same container, so setup is repeatable.
                statement.execute("DROP DATABASE IF EXISTS " + TEMPLATE_DATABASE + " WITH (FORCE)");
                statement.execute("CREATE DATABASE " + TEMPLATE_DATABASE);
            }
            // The template database is fresh, so grant the owner role the privileges it
            // needs to run Liquibase (as the init script does for the main
            // database): CREATE on the database and on its public schema.
            try (Connection admin = openConnection(POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("GRANT CREATE ON DATABASE " + TEMPLATE_DATABASE + " TO " + OWNER_USER);
            }
            try (Connection admin = openConnection(TEMPLATE_DATABASE, POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("GRANT CREATE ON SCHEMA public TO " + OWNER_USER);
                // The test role acts as an application caller; let it create
                // tables it owns so SECURITY INVOKER routines that require
                // ownership (such as ALTER TABLE) can be exercised.
                statement.execute("GRANT CREATE ON SCHEMA public TO " + TEST_USER);
            }
            try (Connection connection = openConnection(TEMPLATE_DATABASE, OWNER_USER, OWNER_PASSWORD)) {
                Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)));
                liquibase.update();
            }
            // Install the static-analysis extension (compiled into the custom
            // image) so every cloned test database has it.
            try (Connection admin = openConnection(TEMPLATE_DATABASE, POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS plpgsql_check");
            }
            // Mark as a real template so nothing can connect to it, which
            // keeps CREATE DATABASE ... TEMPLATE always safe.
            try (Connection admin = openConnection(POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = admin.createStatement()) {
                //noinspection Annotator
                statement.execute("ALTER DATABASE " + TEMPLATE_DATABASE + " WITH IS_TEMPLATE TRUE");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare template database", e);
        }
    }

    private static Connection openConnection(String database, String user, String password) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                        + POSTGRES.getMappedPort(5432) + "/" + database,
                user, password);
    }

    /**
     * Returns the SQLSTATE of the first {@link SQLException} in a throwable's
     * cause chain, or {@code null} when there is none.
     *
     * @param throwable the throwable to inspect
     * @return the SQLSTATE, or {@code null}
     */
    protected static String sqlState(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }

    /**
     * Asserts that a call fails with the given SQLSTATE.
     *
     * @param expectedSqlState the expected SQLSTATE
     * @param call             the call under test
     */
    protected static void assertSqlState(String expectedSqlState, Executable call) {
        DataAccessException exception = assertThrows(DataAccessException.class, call);
        assertEquals(expectedSqlState, sqlState(exception),
                () -> "expected SQLSTATE " + expectedSqlState + " but was: " + exception.getMessage());
    }

    /**
     * Asserts that a call fails with SQLSTATE {@code 23514}
     * ({@code check_violation}), as a domain constraint violation does.
     *
     * @param call the call under test
     */
    protected static void assertDomainViolation(Executable call) {
        assertSqlState("23514", call);
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
     * Creates this test class's private database from the migrated template
     * and opens a jOOQ context to it as the {@code ddl_utils_test} role.
     */
    @BeforeAll
    void createTestDatabase() throws Exception {
        databaseName = "test_" + getClass().getSimpleName().toLowerCase()
                + "_" + Integer.toHexString(getClass().getName().hashCode());
        try (Connection admin = openConnection(POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName + " TEMPLATE " + TEMPLATE_DATABASE);
        }
        connection = openConnection(databaseName, TEST_USER, TEST_PASSWORD);
        dsl = DSL.using(connection, SQLDialect.POSTGRES);
    }

    /**
     * Opens an additional connection to this test class's private database as
     * the test role, for tests that need a second session (for example to hold
     * a lock). The caller is responsible for closing it.
     *
     * @return a new connection to the private test database
     * @throws SQLException if the connection cannot be opened
     */
    protected Connection openTestConnection() throws SQLException {
        return openConnection(databaseName, TEST_USER, TEST_PASSWORD);
    }

    /**
     * Opens a connection to this test class's private database as the schema
     * owner, for tests that must change data the caller role cannot. The
     * caller is responsible for closing it.
     *
     * @return a new owner connection to the private test database
     * @throws SQLException if the connection cannot be opened
     */
    protected Connection openOwnerConnection() throws SQLException {
        return openConnection(databaseName, OWNER_USER, OWNER_PASSWORD);
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
     * Returns the {@code information_schema.columns} row for a column, or
     * {@code null} when the column does not exist.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return the column's information_schema row, or {@code null}
     */
    protected Record column(String schema, String table, String column) {
        return dsl.fetchOne(
                """
                        SELECT *
                        FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = ? AND column_name = ?
                        """,
                schema, table, column);
    }

    /**
     * Returns whether a column exists.
     *
     * @param schema the table schema
     * @param table  the table name
     * @param column the column name
     * @return {@code true} when the column exists
     */
    protected boolean hasColumn(String schema, String table, String column) {
        return column(schema, table, column) != null;
    }

    /**
     * Returns whether a table exists.
     *
     * @param schema the table schema
     * @param table  the table name
     * @return {@code true} when the table exists
     */
    protected boolean tableExists(String schema, String table) {
        return dsl.fetchOne(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = ?
                        )
                        """,
                schema, table).get(0, Boolean.class);
    }

    /**
     * Returns whether a table exists in the public schema.
     *
     * @param table the table name
     * @return {@code true} when the table exists
     */
    protected boolean tableExists(String table) {
        return tableExists(PUBLIC_SCHEMA, table);
    }

    /**
     * Returns a single {@code information_schema.columns} attribute for a
     * column, failing when the column does not exist.
     *
     * @param schema    the table schema
     * @param table     the table name
     * @param column    the column name
     * @param attribute the information_schema column to read
     * @return the attribute value
     */
    protected String columnAttribute(String schema, String table, String column, String attribute) {
        Record record = column(schema, table, column);
        assertNotNull(record, () -> "column not found: " + schema + "." + table + "." + column);
        return record.get(attribute, String.class);
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

    /**
     * Closes the connection and drops this test class's private database.
     */
    @AfterAll
    void dropTestDatabase() throws Exception {
        if (connection != null) {
            connection.close();
        }
        if (databaseName != null) {
            try (Connection admin = openConnection(POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE " + databaseName + " WITH (FORCE)");
            }
        }
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        POSTGRES.start();
        prepareTemplateDatabase();
    }

    /**
     * jOOQ context connected to this test class's private database.
     */
    protected DSLContext dsl;

    /**
     * The schema tests create their own tables in; the test role has CREATE on
     * it.
     */
    protected static final String PUBLIC_SCHEMA = "public";

    private String databaseName;
    private Connection connection;

    private static void prepareTemplateDatabase() {
        try {
            try (Connection admin = openConnection(POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = admin.createStatement()) {
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
     * Creates this test class's private database from the migrated template
     * and opens a jOOQ context to it as the {@code ddl_utils_test} role.
     */
    @BeforeAll
    void createTestDatabase() throws Exception {
        databaseName = "test_" + getClass().getSimpleName().toLowerCase();
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
     * Closes the connection and drops this test class's private database.
     */
    @AfterAll
    void dropTestDatabase() throws Exception {
        if (connection != null) {
            connection.close();
        }
        try (Connection admin = openConnection(POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE " + databaseName);
        }
    }
}

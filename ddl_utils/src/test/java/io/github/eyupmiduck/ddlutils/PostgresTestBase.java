package io.github.eyupmiduck.ddlutils;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

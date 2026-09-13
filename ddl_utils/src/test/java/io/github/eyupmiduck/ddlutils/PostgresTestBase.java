package io.github.eyupmiduck.ddlutils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Base class for tests that need a migrated PostgreSQL database.
 *
 * <p>A single PostgreSQL container is shared by all tests. On first use, the
 * Liquibase changelog is applied once to a template database. Each test class
 * then gets its own private database, created cheaply with
 * {@code CREATE DATABASE ... TEMPLATE ...}, so test data is isolated between
 * classes without re-running migrations. The private database is dropped
 * after the class finishes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PostgresTestBase {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String TEMPLATE_DATABASE = "ddl_utils_template";

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    static {
        POSTGRES.start();
        prepareTemplateDatabase();
    }

    /** jOOQ context connected to this test class's private database. */
    protected DSLContext dsl;

    private String databaseName;
    private Connection connection;

    /**
     * Creates this test class's private database from the migrated template
     * and opens a jOOQ context to it.
     */
    @BeforeAll
    void createTestDatabase() throws Exception {
        databaseName = "test_" + getClass().getSimpleName().toLowerCase();
        try (Connection admin = openConnection(POSTGRES.getDatabaseName());
                Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName + " TEMPLATE " + TEMPLATE_DATABASE);
        }
        connection = openConnection(databaseName);
        dsl = DSL.using(connection, SQLDialect.POSTGRES);
    }

    /**
     * Closes the connection and drops this test class's private database.
     */
    @AfterAll
    void dropTestDatabase() throws Exception {
        if (connection != null) {
            connection.close();
        }
        try (Connection admin = openConnection(POSTGRES.getDatabaseName());
                Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE " + databaseName);
        }
    }

    private static void prepareTemplateDatabase() {
        try {
            try (Connection admin = openConnection(POSTGRES.getDatabaseName());
                    Statement statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE " + TEMPLATE_DATABASE);
            }
            try (Connection connection = openConnection(TEMPLATE_DATABASE)) {
                Liquibase liquibase = new Liquibase(
                        CHANGELOG,
                        new ClassLoaderResourceAccessor(),
                        DatabaseFactory.getInstance()
                                .findCorrectDatabaseImplementation(new JdbcConnection(connection)));
                liquibase.update();
            }
            // Mark as a real template so nothing can connect to it, which
            // keeps CREATE DATABASE ... TEMPLATE always safe.
            try (Connection admin = openConnection(POSTGRES.getDatabaseName());
                    Statement statement = admin.createStatement()) {
                statement.execute("ALTER DATABASE " + TEMPLATE_DATABASE + " WITH IS_TEMPLATE TRUE");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare template database", e);
        }
    }

    private static Connection openConnection(String database) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                        + POSTGRES.getMappedPort(5432) + "/" + database,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }
}

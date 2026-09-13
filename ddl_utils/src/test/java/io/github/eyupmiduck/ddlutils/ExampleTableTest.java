package io.github.eyupmiduck.ddlutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.github.eyupmiduck.ddlutils.jooq.tables.Example;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Verifies that the Liquibase changelog creates the {@code ddl_utils} schema
 * and the {@code example} table on a real PostgreSQL database.
 */
@Testcontainers
class ExampleTableTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    /**
     * Applies the Liquibase changelog to a fresh PostgreSQL container and
     * checks that the {@code ddl_utils} schema exists and the
     * {@code example} table can be queried through the generated jOOQ
     * classes.
     */
    @Test
    void exampleTableExistsAfterMigration() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(connection)));
            liquibase.update();

            DSLContext dsl = DSL.using(connection, SQLDialect.POSTGRES);
            assertTrue(dsl.meta().getSchemas().stream()
                    .anyMatch(schema -> schema.getName().equals("ddl_utils")));
            assertEquals(0, dsl.selectCount().from(Example.EXAMPLE).fetchOne(0, int.class));
        }
    }
}

package io.github.eyupmiduck.ddlutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.eyupmiduck.ddlutils.jooq.tables.Example;

/**
 * Verifies that the Liquibase changelog creates the {@code ddl_utils} schema
 * and the {@code example} table on a real PostgreSQL database.
 */
class ExampleTableTest extends PostgresTestBase {

    /**
     * Checks that the {@code ddl_utils} schema exists and the {@code example}
     * table can be queried through the generated jOOQ classes in the
     * per-class database cloned from the migrated template.
     */
    @Test
    void exampleTableExistsAfterMigration() {
        assertTrue(dsl.meta().getSchemas().stream()
                .anyMatch(schema -> schema.getName().equals("ddl_utils")));
        assertEquals(0, dsl.selectCount().from(Example.EXAMPLE).fetchOne(0, int.class));
    }
}

package io.github.eyupmiduck.ddlutils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.eyupmiduck.ddlutils.jooq.tables.Example;

/**
 * Verifies that data written by one test class is not visible to others:
 * this class writes a row, while {@link ExampleTableTest} always sees an
 * empty {@code example} table, because each class gets its own database
 * cloned from the template.
 */
class ExampleDataTest extends PostgresTestBase {

    /**
     * Inserts a row into {@code example} and checks it can be read back in
     * this class's private database.
     */
    @Test
    void insertedRowIsVisibleInOwnDatabase() {
        dsl.insertInto(Example.EXAMPLE, Example.EXAMPLE.NAME)
                .values("test")
                .execute();

        assertEquals(1, dsl.selectCount().from(Example.EXAMPLE).fetchOne(0, int.class));
    }
}

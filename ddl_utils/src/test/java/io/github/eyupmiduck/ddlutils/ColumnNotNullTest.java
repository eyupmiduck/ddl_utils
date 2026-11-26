package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for {@code ddl_utils_lib.column_not_null}: it reads
 * {@code attnotnull} for a live column and raises for a missing one.
 */
class ColumnNotNullTest extends PostgresTestBase {

    private static final String TABLE = "column_not_null_target";

    @BeforeEach
    void createTable() {
        createTestTable(TABLE, "id int, note text NOT NULL");
    }

    @AfterEach
    void dropTable() {
        dropTestTable(TABLE);
    }

    /**
     * A NOT NULL column returns true and a nullable column returns false.
     */
    @Test
    void reportsNullability() {
        assertEquals(true, notNull("note"));
        assertEquals(false, notNull("id"));
    }

    /**
     * A missing column raises undefined_column rather than reading as nullable.
     */
    @Test
    void rejectsMissingColumn() {
        assertSqlState("42703", () -> notNull("missing"));
    }

    private boolean notNull(String column) {
        return evaluate("ddl_utils_lib.column_not_null('" + PUBLIC_SCHEMA + "." + TABLE
                + "'::regclass, '" + TABLE + "', '" + column + "', 'test')", Boolean.class);
    }
}

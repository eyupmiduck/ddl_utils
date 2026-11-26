package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for {@code ddl_utils_lib.column_exists}: it reports a live
 * column, a missing column and a dropped column without raising.
 */
class ColumnExistsTest extends PostgresTestBase {

    /**
     * A present column returns true and an unknown column returns false.
     */
    @Test
    void reportsPresentAndMissingColumns() {
        String table = "column_exists_target";
        createTestTable(table, "id int, note text");
        try {
            assertEquals(true, columnExists(table, "note"));
            assertEquals(false, columnExists(table, "missing"));
        } finally {
            dropTestTable(table);
        }
    }

    /**
     * A dropped column is reported as absent even though its attribute row may
     * remain as a dropped placeholder.
     */
    @Test
    void reportsDroppedColumnAsAbsent() {
        String table = "column_exists_dropped_target";
        createTestTable(table, "id int, note text");
        try {
            dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + table + " DROP COLUMN note");
            assertEquals(false, columnExists(table, "note"));
        } finally {
            dropTestTable(table);
        }
    }

    private boolean columnExists(String table, String column) {
        return evaluate("ddl_utils_lib.column_exists('" + PUBLIC_SCHEMA + "." + table
                + "'::regclass, '" + column + "')", Boolean.class);
    }
}

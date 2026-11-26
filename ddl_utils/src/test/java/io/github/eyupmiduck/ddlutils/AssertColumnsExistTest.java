package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@code ddl_utils_lib.assert_columns_exist}: it accepts a
 * list whose columns all exist and raises undefined_column for a missing name.
 */
class AssertColumnsExistTest extends PostgresTestBase {

    private static final String TABLE = "assert_columns_exist_target";

    @BeforeEach
    void createTable() {
        createTestTable(TABLE, "id int, note text");
    }

    @AfterEach
    void dropTable() {
        dropTestTable(TABLE);
    }

    /**
     * Every named column exists, so the helper returns normally.
     */
    @Test
    void acceptsExistingColumns() {
        assertColumns("id", "note");
    }

    /**
     * A missing column raises undefined_column.
     */
    @Test
    void rejectsMissingColumn() {
        assertSqlState("42703", () -> assertColumns("id", "missing"));
    }

    private void assertColumns(String... columns) {
        String array = "ARRAY['" + String.join("','", columns)
                + "']::ddl_utils.non_empty_non_null_text_array";
        dsl.execute("SELECT ddl_utils_lib.assert_columns_exist('" + PUBLIC_SCHEMA + "." + TABLE
                + "'::regclass, '" + PUBLIC_SCHEMA + "', '" + TABLE + "', " + array + ", 'test')");
    }
}

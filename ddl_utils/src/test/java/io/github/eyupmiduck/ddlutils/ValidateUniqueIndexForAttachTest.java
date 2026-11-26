package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for {@code ddl_utils_lib.validate_unique_index_for_attach}: it
 * accepts a valid unique index and rejects missing, non-unique, partial and
 * expression indexes.
 */
class ValidateUniqueIndexForAttachTest extends PostgresTestBase {

    private static final String TABLE = "validate_index_target";

    @BeforeEach
    void createTable() {
        createTestTable(TABLE, "id int, note text");
        dsl.execute("CREATE UNIQUE INDEX uq_id ON " + PUBLIC_SCHEMA + "." + TABLE + " (id)");
        dsl.execute("CREATE INDEX idx_note ON " + PUBLIC_SCHEMA + "." + TABLE + " (note)");
        dsl.execute("CREATE UNIQUE INDEX uq_note_partial ON " + PUBLIC_SCHEMA + "." + TABLE
                + " (note) WHERE note IS NOT NULL");
        dsl.execute("CREATE UNIQUE INDEX uq_note_expr ON " + PUBLIC_SCHEMA + "." + TABLE
                + " (lower(note))");
    }

    @AfterEach
    void dropTable() {
        dropTestTable(TABLE);
    }

    /**
     * A valid, unique, non-partial, non-expression index is accepted.
     */
    @Test
    void acceptsUsableUniqueIndex() {
        validate(TABLE, "uq_id");
    }

    /**
     * Non-unique, partial and expression indexes are rejected with 42809.
     */
    @Test
    void rejectsUnusableIndexes() {
        assertSqlState("42809", () -> validate(TABLE, "idx_note"));
        assertSqlState("42809", () -> validate(TABLE, "uq_note_partial"));
        assertSqlState("42809", () -> validate(TABLE, "uq_note_expr"));
    }

    /**
     * A missing index is 42704 and a missing table is 42P01.
     */
    @Test
    void rejectsMissingIndexAndTable() {
        assertSqlState("42704", () -> validate(TABLE, "missing"));
        assertSqlState("42P01", () -> validate("no_such_table", "uq_id"));
    }

    private void validate(String table, String index) {
        dsl.execute("SELECT ddl_utils_lib.validate_unique_index_for_attach('" + PUBLIC_SCHEMA
                + "', '" + table + "', '" + index + "', 'test')");
    }
}

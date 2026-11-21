package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.drop_column_default}: it builds an
 * {@code ALTER COLUMN ... DROP DEFAULT} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class DropColumnDefaultTest extends SingleTableTest {

    DropColumnDefaultTest() {
        super("drop_column_default_target", "id int, note text DEFAULT 'none'");
    }

    /**
     * Drops the default, leaving the column in place.
     */
    @Test
    void dropsDefault() {
        assertColumnDefault(PUBLIC_SCHEMA, target(), "note", "'none'");

        dropColumnDefault("note");

        assertNoColumnDefault(PUBLIC_SCHEMA, target(), "note");
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * Dropping the default of a column that has none is a no-op, not an error.
     */
    @Test
    void dropsDefaultWhenAbsent() {
        assertNoColumnDefault(PUBLIC_SCHEMA, target(), "id");

        dropColumnDefault("id");

        assertNoColumnDefault(PUBLIC_SCHEMA, target(), "id");
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
    }

    /**
     * Rejects null arguments through their domains before the body runs.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> dropColumnDefault(null));
        assertDomainViolation(() -> dropColumnDefault("note", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumnDefault("note", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumnDefault("note", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    /**
     * An over-long column name is rejected with 22023 before the ALTER; a
     * 63-byte name passes the length guard and fails later only because no such
     * column exists (42703).
     */
    @Test
    void boundsColumnNameLength() {
        assertSqlState("22023", () -> dropColumnDefault("c".repeat(64)));
        assertSqlState("42703", () -> dropColumnDefault("c".repeat(63)));
    }

    private void dropColumnDefault(String column) {
        dropColumnDefault(column, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void dropColumnDefault(String column, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.dropColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, target(), column,
                lockTimeout, sleepTime, duration);
    }
}

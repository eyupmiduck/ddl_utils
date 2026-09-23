package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.drop_column_default}: it builds an
 * {@code ALTER COLUMN ... DROP DEFAULT} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class DropColumnDefaultTest extends SingleTableTest {

    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    DropColumnDefaultTest() {
        super("drop_column_default_target", "id int, note text DEFAULT 'none'");
    }

    /**
     * Drops the default, leaving the column in place.
     */
    @Test
    void dropsDefault() {
        dropColumnDefault("note");

        assertNull(columnAttribute(PUBLIC_SCHEMA, target(), "note", "column_default"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * Dropping the default of a column that has none is a no-op, not an error.
     */
    @Test
    void dropsDefaultWhenAbsent() {
        dropColumnDefault("id");

        assertNull(columnAttribute(PUBLIC_SCHEMA, target(), "id", "column_default"));
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

    private void dropColumnDefault(String column) {
        dropColumnDefault(column, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void dropColumnDefault(String column, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.dropColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, target(), column,
                lockTimeout, sleepTime, duration);
    }
}

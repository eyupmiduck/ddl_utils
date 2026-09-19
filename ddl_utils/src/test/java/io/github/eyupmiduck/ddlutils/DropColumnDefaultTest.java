package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.drop_column_default}: it builds an
 * {@code ALTER COLUMN ... DROP DEFAULT} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class DropColumnDefaultTest extends PostgresTestBase {

    private static final String TARGET = "drop_column_default_target";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, note text DEFAULT 'none'");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Drops the default, leaving the column in place.
     */
    @Test
    void dropsDefault() {
        dropColumnDefault("note");

        assertNull(columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "column_default"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * Dropping the default of a column that has none is a no-op, not an error.
     */
    @Test
    void dropsDefaultWhenAbsent() {
        dropColumnDefault("id");

        assertNull(columnAttribute(PUBLIC_SCHEMA, TARGET, "id", "column_default"));
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
        Routines.dropColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column,
                lockTimeout, sleepTime, duration);
    }
}

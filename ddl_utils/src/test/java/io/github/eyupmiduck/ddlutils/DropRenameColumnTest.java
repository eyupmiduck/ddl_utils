package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the structured column helpers {@code ddl_utils_lib.drop_column} and
 * {@code ddl_utils_lib.rename_column}: each builds an identifier-safe fragment
 * and runs it through {@code ddl_utils_lib.alter_table}.
 */
class DropRenameColumnTest extends SingleTableTest {

    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    DropRenameColumnTest() {
        super("drop_rename_column_target", "id int, keep int, old_name int");
    }

    /**
     * drop_column removes the named column and leaves the others.
     */
    @Test
    void dropsColumn() {
        dropColumn("old_name", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
    }

    /**
     * drop_columns removes several columns in one ALTER TABLE and leaves the
     * others.
     */
    @Test
    void dropsMultipleColumns() {
        dropColumns(new String[]{"old_name", "keep"}, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
    }

    /**
     * drop_columns rejects an element that is blank, including whitespace other
     * than spaces, and leaves the table unchanged.
     */
    @Test
    void dropColumnsRejectsBlankName() {
        assertSqlState("22023",
                () -> dropColumns(new String[]{"  "}, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertSqlState("22023",
                () -> dropColumns(new String[]{"\t\n"}, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
    }

    /**
     * drop_columns rejects duplicate column names with an invalid-parameter
     * error, leaving the table unchanged.
     */
    @Test
    void dropColumnsRejectsDuplicateNames() {
        assertSqlState("22023",
                () -> dropColumns(new String[]{"old_name", "old_name"},
                        DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
    }

    /**
     * drop_columns rejects a null array and a null element through the array
     * domain.
     */
    @Test
    void dropColumnsRejectsNullArrayAndElements() {
        assertDomainViolation(() -> dropColumns(null, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumns(new String[]{null}, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }

    /**
     * drop_columns rejects an array that does not start at index 1 with an
     * invalid-parameter error (the fragment builder is 1-based), leaving the
     * table unchanged.
     */
    @Test
    void dropColumnsRejectsNonOneBasedArray() {
        assertSqlState("22023", () -> dsl.execute(
                "SELECT ddl_utils_lib.drop_columns(?, ?, '[0:1]={old_name,keep}'::text[], ?, ?, ?)",
                PUBLIC_SCHEMA, target(), DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
    }

    /**
     * rename_column renames a column without touching the others.
     */
    @Test
    void renamesColumn() {
        renameColumn("old_name", "new_name", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "new_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
    }

    /**
     * Null identifiers are rejected through the domains.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> dropColumn(null, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> renameColumn(null, "new_name", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> renameColumn("old_name", null, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }

    private void dropColumn(String column, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.dropColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), column, lockTimeout, sleepTime, duration);
    }

    private void dropColumns(String[] columns, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.dropColumns(dsl.configuration(), PUBLIC_SCHEMA, target(), columns, lockTimeout, sleepTime, duration);
    }

    private void renameColumn(String column, String newName, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.renameColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), column, newName, lockTimeout, sleepTime,
                duration);
    }
}

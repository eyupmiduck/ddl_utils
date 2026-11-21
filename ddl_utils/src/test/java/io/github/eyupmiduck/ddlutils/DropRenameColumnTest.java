package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the structured column helpers {@code ddl_utils_lib.drop_column},
 * {@code ddl_utils_lib.drop_columns} and {@code ddl_utils_lib.rename_column}:
 * each builds an identifier-safe fragment and runs it through
 * {@code ddl_utils_lib.alter_table}.
 */
class DropRenameColumnTest extends SingleTableTest {

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
     * drop_columns rejects a list that is both blank and duplicated with the
     * blank-element error, naming the position: ARRAY['', ''] must not report a
     * duplicate of an invisible name.
     */
    @Test
    void dropColumnsRejectsBlankNamesBeforeDuplicates() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> dropColumns(new String[]{"", ""}, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("column name at position 1 is blank"),
                () -> "the blank error must win and name the position: " + failure.getMessage());
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
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
     * error that names the duplicate, leaving the table unchanged.
     */
    @Test
    void dropColumnsRejectsDuplicateNames() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> dropColumns(new String[]{"old_name", "old_name"},
                        DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("old_name"),
                () -> "the error should name the duplicate: " + failure.getMessage());
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
    }

    /**
     * Two names that differ only after the 63-byte identifier limit collapse to
     * the same PostgreSQL identifier and are rejected as duplicates. The error
     * names the duplicate and says it is a duplicate, so it cannot be confused
     * with the separate 63-byte length rejection.
     */
    @Test
    void dropColumnsRejectsNamesThatCollapseToTheSameIdentifier() {
        String base = "a".repeat(63);

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> dropColumns(new String[]{base + "x", base + "y"},
                        DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("duplicate"),
                () -> "the error must be the duplicate rejection, not a length rejection: "
                        + failure.getMessage());
        assertTrue(failure.getMessage().contains(base + "x"),
                () -> "the error should name the collapsed duplicate: " + failure.getMessage());
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
    }

    /**
     * drop_columns rejects a null array and a null element through the array
     * domain.
     */
    @Test
    void dropColumnsRejectsNullArrayAndElements() {
        assertDomainViolation(() -> dropColumns(null, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumns(new String[]{null}, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
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
     * rename_column rejects an identifier over PostgreSQL's 63-byte limit
     * before the ALTER, leaving the table unchanged. The DETAIL names both
     * arguments and their byte lengths so the caller can see which is
     * over-long.
     */
    @Test
    void renameColumnRejectsOverLongName() {
        String overLong = "c".repeat(64);

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> renameColumn("old_name", overLong, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("i_column_name is 8 bytes"),
                () -> "the DETAIL should give i_column_name's length: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("i_new_column_name is 64 bytes"),
                () -> "the DETAIL should give i_new_column_name's length: " + failure.getMessage());
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), overLong));
    }

    /**
     * rename_column accepts a new name of exactly 63 bytes (NAMEDATALEN - 1),
     * the largest identifier PostgreSQL stores without truncation.
     */
    @Test
    void renameColumnAcceptsNameAtByteLimit() {
        String atLimit = "c".repeat(63);

        renameColumn("old_name", atLimit, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), atLimit));
    }

    /**
     * The 63-byte limit is measured in bytes, not characters: 32 two-byte
     * characters are 64 bytes and would be truncated by PostgreSQL, so the
     * rename is rejected and the old name survives.
     */
    @Test
    void renameColumnRejectsMultibyteOverLongName() {
        assertSqlState("22023", () -> renameColumn(
                "old_name", "\u00e9".repeat(32), DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
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

    /**
     * The nullable integer settings are bound in the right order for each
     * helper: null and negative values for the lock timeout, sleep time and
     * duration are all rejected through the domains, leaving the table
     * unchanged.
     */
    @Test
    void rejectsInvalidSettingsArguments() {
        assertDomainViolation(() -> dropColumn("old_name", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumn("old_name", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumn("old_name", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
        assertDomainViolation(() -> dropColumn("old_name", -1, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumn("old_name", DDL_LOCK_TIMEOUT, -1, STATEMENT_DURATION));
        assertDomainViolation(() -> dropColumn("old_name", DDL_LOCK_TIMEOUT, SLEEP_TIME, -1));

        assertDomainViolation(() -> renameColumn("old_name", "new_name", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> renameColumn("old_name", "new_name", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> renameColumn("old_name", "new_name", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
        assertDomainViolation(() -> renameColumn("old_name", "new_name", -1, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> renameColumn("old_name", "new_name", DDL_LOCK_TIMEOUT, -1, STATEMENT_DURATION));
        assertDomainViolation(() -> renameColumn("old_name", "new_name", DDL_LOCK_TIMEOUT, SLEEP_TIME, -1));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "new_name"));
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

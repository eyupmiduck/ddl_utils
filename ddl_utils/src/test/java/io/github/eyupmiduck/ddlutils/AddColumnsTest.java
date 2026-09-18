package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.add_columns}: it builds a single ADD COLUMN
 * fragment from parallel arrays and applies it through
 * {@code ddl_utils_lib.alter_table}, validating that the arrays have equal lengths.
 */
class AddColumnsTest extends PostgresTestBase {

    private static final String TARGET = "add_columns_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Adds two columns in one call, honouring per-column defaults and the
     * nullable flag.
     */
    @Test
    void addsMultipleColumnsInOneCall() {
        addColumns(
                new String[]{"first", "second"},
                new String[]{"int", "text"},
                new String[]{null, "'x'"},
                new Boolean[]{true, false},
                1000, 10, 5000);

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "second"));
        assertEquals("YES", columnAttribute(PUBLIC_SCHEMA, TARGET, "first", "is_nullable"));
        assertNull(columnAttribute(PUBLIC_SCHEMA, TARGET, "first", "column_default"));
        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "second", "is_nullable"));
        assertTrue(columnAttribute(PUBLIC_SCHEMA, TARGET, "second", "column_default").contains("'x'"));
    }

    /**
     * Rejects parallel arrays whose lengths differ, with an invalid-parameter
     * error and without altering the table.
     */
    @Test
    void rejectsMismatchedArrayLengths() {
        // types shorter than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first", "second"},
                new String[]{"int"},
                new String[]{null, null},
                new Boolean[]{true, true},
                1000, 10, 5000));
        // defaults shorter than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first", "second"},
                new String[]{"int", "text"},
                new String[]{null},
                new Boolean[]{true, true},
                1000, 10, 5000));
        // nullable shorter than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first", "second"},
                new String[]{"int", "text"},
                new String[]{null, null},
                new Boolean[]{true},
                1000, 10, 5000));
        // defaults longer than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{null, null},
                new Boolean[]{true},
                1000, 10, 5000));
        // nullable longer than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{null},
                new Boolean[]{true, true},
                1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "second"));
    }

    /**
     * Rejects a blank default expression with an invalid-parameter error and
     * without altering the table.
     */
    @Test
    void rejectsBlankDefault() {
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{"   "},
                new Boolean[]{true},
                1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
    }

    /**
     * Rejects blank column names and types with an invalid-parameter error,
     * leaving the table unchanged.
     */
    @Test
    void rejectsBlankColumnNameAndType() {
        assertSqlState("22023", () -> addColumns(
                new String[]{"  "},
                new String[]{"int"},
                new String[]{null},
                new Boolean[]{true},
                1000, 10, 5000));
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"  "},
                new String[]{null},
                new Boolean[]{true},
                1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
    }

    /**
     * Rejects a column type with a top-level comma, which could otherwise
     * append extra clauses to the generated ALTER TABLE, leaving the table
     * unchanged.
     */
    @Test
    void rejectsTopLevelCommaInType() {
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int, DROP COLUMN id"},
                new String[]{null},
                new Boolean[]{true},
                1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "id"));
    }

    /**
     * Rejects a default value with a top-level comma, which could otherwise
     * append extra clauses to the generated ALTER TABLE, leaving the table
     * unchanged.
     */
    @Test
    void rejectsTopLevelCommaInDefault() {
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{"0, ADD COLUMN backdoor int"},
                new Boolean[]{true},
                1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "backdoor"));
    }

    /**
     * Allows commas that are inside parentheses or string literals, as in a
     * numeric(10,2) type or a coalesce / quoted default.
     */
    @Test
    void allowsCommasInsideParenthesesAndLiterals() {
        addColumns(
                new String[]{"amount", "label"},
                new String[]{"numeric(10,2)", "text"},
                new String[]{"coalesce(1, 2)", "'a,b'"},
                new Boolean[]{true, true},
                1000, 10, 5000);

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "amount"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "label"));
    }

    /**
     * Rejects empty arrays through the array domains before the body runs.
     */
    @Test
    void rejectsEmptyArraysThroughDomains() {
        assertDomainViolation(() -> addColumns(
                new String[0], new String[0], new String[0], new Boolean[0], 1000, 10, 5000));
    }

    /**
     * Rejects a null element in the non-null column name, type, and nullable
     * arrays through the array domains before the body runs.
     */
    @Test
    void rejectsNullElementInNonNullArrays() {
        assertDomainViolation(() -> addColumns(
                new String[]{null}, new String[]{"int"}, new String[]{null}, new Boolean[]{true},
                1000, 10, 5000));
        assertDomainViolation(() -> addColumns(
                new String[]{"first"}, new String[]{null}, new String[]{null}, new Boolean[]{true},
                1000, 10, 5000));
        assertDomainViolation(() -> addColumns(
                new String[]{"first"}, new String[]{"int"}, new String[]{null}, new Boolean[]{null},
                1000, 10, 5000));
    }

    private void addColumns(String[] names, String[] types, String[] defaults, Boolean[] nullable,
                            Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.addColumns(dsl.configuration(), PUBLIC_SCHEMA, TARGET, names, types, defaults,
                nullable, lockTimeout, sleepTime, duration);
    }
}

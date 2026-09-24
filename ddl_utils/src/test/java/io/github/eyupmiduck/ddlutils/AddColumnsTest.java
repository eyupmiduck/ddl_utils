package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.add_columns}: it builds a single ADD COLUMN
 * fragment from parallel arrays and applies it through
 * {@code ddl_utils_lib.alter_table}, validating that the arrays have equal lengths.
 */
class AddColumnsTest extends SingleTableTest {

    AddColumnsTest() {
        super("add_columns_target", "id int");
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
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "first"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "second"));
        assertNullable(PUBLIC_SCHEMA, target(), "first");
        assertNoColumnDefault(PUBLIC_SCHEMA, target(), "first");
        assertNotNullable(PUBLIC_SCHEMA, target(), "second");
        assertColumnDefault(PUBLIC_SCHEMA, target(), "second", "'x'");
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
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        // defaults shorter than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first", "second"},
                new String[]{"int", "text"},
                new String[]{null},
                new Boolean[]{true, true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        // nullable shorter than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first", "second"},
                new String[]{"int", "text"},
                new String[]{null, null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        // defaults longer than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{null, null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        // nullable longer than names
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{null},
                new Boolean[]{true, true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "first"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "second"));
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
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{"\t\n"},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "first"));
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
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"  "},
                new String[]{null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertSqlState("22023", () -> addColumns(
                new String[]{"\t\n"},
                new String[]{"int"},
                new String[]{null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"\t\n"},
                new String[]{null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "first"));
    }

    /**
     * Rejects a type that is not a single SQL type: an unknown type, a type
     * with an appended clause, and a type with a comma that would add another
     * clause. The table is left unchanged.
     */
    @Test
    void rejectsInvalidType() {
        // unknown type
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"no_such_type"},
                new String[]{null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        // appended clause
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int DEFAULT 0"},
                new String[]{null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        // comma would append another action
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int, DROP COLUMN id"},
                new String[]{null},
                new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "first"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
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
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "first"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "backdoor"));
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
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "amount"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "label"));
    }

    /**
     * Rejects empty arrays through the array domains before the body runs.
     */
    @Test
    void rejectsEmptyArraysThroughDomains() {
        assertDomainViolation(() -> addColumns(
                new String[0], new String[0], new String[0], new Boolean[0], DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }

    /**
     * Rejects arrays that do not start at index 1 with an invalid-parameter
     * error (the fragment builder is 1-based), leaving the table unchanged.
     */
    @Test
    void rejectsNonOneBasedArrays() {
        assertSqlState("22023", () -> dsl.execute(
                "SELECT ddl_utils_lib.add_columns(?, ?, '[0:1]={first,second}'::text[], "
                        + "ARRAY['int', 'text'], ARRAY[NULL::text, NULL::text], ARRAY[true, true], ?, ?, ?)",
                PUBLIC_SCHEMA, target(), DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "first"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "second"));
    }

    /**
     * Rejects a null element in the non-null column name, type, and nullable
     * arrays through the array domains before the body runs.
     */
    @Test
    void rejectsNullElementInNonNullArrays() {
        assertDomainViolation(() -> addColumns(
                new String[]{null}, new String[]{"int"}, new String[]{null}, new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> addColumns(
                new String[]{"first"}, new String[]{null}, new String[]{null}, new Boolean[]{true},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> addColumns(
                new String[]{"first"}, new String[]{"int"}, new String[]{"'x'"}, new Boolean[]{null},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }

    private void addColumns(String[] names, String[] types, String[] defaults, Boolean[] nullable,
                            Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.addColumns(dsl.configuration(), PUBLIC_SCHEMA, target(), names, types, defaults,
                nullable, lockTimeout, sleepTime, duration);
    }
}

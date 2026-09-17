package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertSqlState("22023", () -> addColumns(
                new String[]{"first", "second"},
                new String[]{"int"},
                new String[]{null, null},
                new Boolean[]{true, true},
                1000, 10, 5000));

        assertTrue(!hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
    }

    /**
     * Rejects a blank default expression with an invalid-parameter error.
     */
    @Test
    void rejectsBlankDefault() {
        assertSqlState("22023", () -> addColumns(
                new String[]{"first"},
                new String[]{"int"},
                new String[]{"   "},
                new Boolean[]{true},
                1000, 10, 5000));
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

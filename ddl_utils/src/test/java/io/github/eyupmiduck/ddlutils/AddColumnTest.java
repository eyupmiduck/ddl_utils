package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.add_column}: it builds an ADD COLUMN fragment and
 * applies it through {@code ddl_utils_lib.alter_table}, honouring the nullable flag
 * and an optional default expression.
 */
class AddColumnTest extends PostgresTestBase {

    private static final String TARGET = "add_column_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Adds a nullable column with no default; the column accepts nulls and has
     * no default.
     */
    @Test
    void addsNullableColumnWithoutDefault() {
        addColumn("note", "text", null, true, 1000, 10, 5000);

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "note"));
        assertEquals("YES", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertNull(columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "column_default"));
    }

    /**
     * Adds a column with a default SQL expression, which becomes the column
     * default.
     */
    @Test
    void addsColumnWithDefaultExpression() {
        addColumn("created", "timestamptz", "now()", true, 1000, 10, 5000);

        assertTrue(columnAttribute(PUBLIC_SCHEMA, TARGET, "created", "column_default").contains("now()"));
    }

    /**
     * Adds a NOT NULL column with a literal default.
     */
    @Test
    void addsNotNullColumnWithDefault() {
        addColumn("count", "int", "0", false, 1000, 10, 5000);

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "count", "is_nullable"));
        String defaultExpression = columnAttribute(PUBLIC_SCHEMA, TARGET, "count", "column_default");
        assertNotNull(defaultExpression);
        assertTrue(defaultExpression.contains("0"));
    }

    /**
     * Adding a NOT NULL column with no default to a table that already has rows
     * fails, leaving the table unchanged.
     */
    @Test
    void rejectsNotNullColumnWithoutDefaultOnPopulatedTable() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id) VALUES (1)");

        assertSqlState("23502", () -> addColumn("required", "int", null, false, 1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "required"));
    }

    /**
     * Rejects a blank default expression, delegated to
     * {@code ddl_utils_lib.add_columns}, with an invalid-parameter error.
     */
    @Test
    void rejectsBlankDefault() {
        assertSqlState("22023", () -> addColumn("note", "text", "   ", true, 100, 100, 1000));
    }

    /**
     * Rejects null for each mandatory text argument through its domain.
     */
    @Test
    void rejectsNullTextArguments() {
        assertDomainViolation(() -> addColumn(null, "text", null, true, 100, 100, 1000));
        assertDomainViolation(() -> addColumn("note", null, null, true, 100, 100, 1000));
    }

    /**
     * Rejects null for the nullable flag and null or negative values for the
     * integer arguments through their domains.
     */
    @Test
    void rejectsNullAndNegativeArguments() {
        // nullable is null
        assertDomainViolation(() -> addColumn("note", "text", null, null, 100, 100, 1000));
        // ddl_lock_timeout is null / negative
        assertDomainViolation(() -> addColumn("note", "text", null, true, null, 100, 1000));
        assertDomainViolation(() -> addColumn("note", "text", null, true, -1, 100, 1000));
        // sleep_time is null / negative
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, null, 1000));
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, -1, 1000));
        // statement_duration is null / negative
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, 100, null));
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, 100, -1));
    }

    private void addColumn(String column, String type, String defaultValue, Boolean nullable,
                           Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.addColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column, type, nullable,
                defaultValue, lockTimeout, sleepTime, duration);
    }
}

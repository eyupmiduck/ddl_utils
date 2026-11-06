package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.add_column}: it builds an ADD COLUMN fragment and
 * applies it through {@code ddl_utils_lib.alter_table}, honouring the nullable flag
 * and an optional default expression.
 */
class AddColumnTest extends SingleTableTest {

    AddColumnTest() {
        super("add_column_target", "id int");
    }

    /**
     * Adds a nullable column with no default; the column accepts nulls and has
     * no default.
     */
    @Test
    void addsNullableColumnWithoutDefault() {
        addColumn("note", "text", true, null, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "note"));
        assertEquals("text", columnAttribute(PUBLIC_SCHEMA, target(), "note", "data_type"));
        assertNullable(PUBLIC_SCHEMA, target(), "note");
        assertNoColumnDefault(PUBLIC_SCHEMA, target(), "note");
    }

    /**
     * Adds a column with a default SQL expression, which becomes the column
     * default.
     */
    @Test
    void addsColumnWithDefaultExpression() {
        addColumn("created", "timestamptz", true, "now()", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "created"));
        assertEquals("timestamp with time zone",
                columnAttribute(PUBLIC_SCHEMA, target(), "created", "data_type"));
        assertNullable(PUBLIC_SCHEMA, target(), "created");
        assertEquals("now()", columnAttribute(PUBLIC_SCHEMA, target(), "created", "column_default"),
                "the default must be exactly now(), not merely contain it");
    }

    /**
     * Adds a NOT NULL column with a literal default.
     */
    @Test
    void addsNotNullColumnWithDefault() {
        addColumn("count", "int", false, "0", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertNotNullable(PUBLIC_SCHEMA, target(), "count");
        assertEquals("integer", columnAttribute(PUBLIC_SCHEMA, target(), "count", "data_type"));
        assertColumnDefault(PUBLIC_SCHEMA, target(), "count", "0");
    }

    /**
     * A quoted literal default must carry its own quotes; the stored default is
     * the text literal, not the identifier {@code none}.
     */
    @Test
    void addsQuotedLiteralDefault() {
        addColumn("status", "text", true, "'none'", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertEquals("'none'::text",
                columnAttribute(PUBLIC_SCHEMA, target(), "status", "column_default"));
    }

    /**
     * A default that tries to append another ALTER TABLE action is rejected by
     * the top-level-comma guard, leaving the table unchanged.
     */
    @Test
    void rejectsInjectedDefault() {
        assertSqlState("22023", () -> addColumn(
                "note", "text", true, "'a', DROP COLUMN id",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "note"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
    }

    /**
     * Adding a NOT NULL column with no default to a table that already has rows
     * fails, leaving the table unchanged.
     */
    @Test
    void rejectsNotNullColumnWithoutDefaultOnPopulatedTable() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id) VALUES (1)");

        assertSqlState("23502", () -> addColumn("required", "int", false, null, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "required"));
        assertEquals(1, dsl.fetchOne("SELECT count(*)::int FROM " + PUBLIC_SCHEMA + "." + target())
                .get(0, Integer.class));
    }

    /**
     * Rejects a blank default expression, delegated to
     * {@code ddl_utils_lib.add_columns}, with an invalid-parameter error.
     */
    @Test
    void rejectsBlankDefault() {
        assertSqlState("22023", () -> addColumn("note", "text", true, "   ", 100, 100, 1000));
    }

    /**
     * Rejects null for each mandatory text argument through its domain.
     */
    @Test
    void rejectsNullTextArguments() {
        assertDomainViolation(() -> addColumn(null, "text", true, null, 100, 100, 1000));
        assertDomainViolation(() -> addColumn("note", null, true, null, 100, 100, 1000));
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
        assertDomainViolation(() -> addColumn("note", "text", true, null, null, 100, 1000));
        assertDomainViolation(() -> addColumn("note", "text", true, null, -1, 100, 1000));
        // sleep_time is null / negative
        assertDomainViolation(() -> addColumn("note", "text", true, null, 100, null, 1000));
        assertDomainViolation(() -> addColumn("note", "text", true, null, 100, -1, 1000));
        // statement_duration is null / negative
        assertDomainViolation(() -> addColumn("note", "text", true, null, 100, 100, null));
        assertDomainViolation(() -> addColumn("note", "text", true, null, 100, 100, -1));
    }

    private void addColumn(String column, String type, Boolean nullable, String defaultValue,
                           Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.addColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), column, type, nullable,
                defaultValue, lockTimeout, sleepTime, duration);
    }
}

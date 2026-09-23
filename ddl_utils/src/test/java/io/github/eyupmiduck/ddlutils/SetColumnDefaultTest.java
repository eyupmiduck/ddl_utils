package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.set_column_default}: it builds an
 * {@code ALTER COLUMN ... SET DEFAULT} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class SetColumnDefaultTest extends SingleTableTest {

    SetColumnDefaultTest() {
        super("set_column_default_target", "id int, note text");
    }

    /**
     * Sets a literal default on a column that had none.
     */
    @Test
    void setsLiteralDefault() {
        setColumnDefault("note", "'none'");

        assertColumnDefault(PUBLIC_SCHEMA, target(), "note", "'none'");
    }

    /**
     * Sets an expression default, which is spliced in as raw SQL.
     */
    @Test
    void setsExpressionDefault() {
        setColumnDefault("note", "upper('a')");

        assertColumnDefault(PUBLIC_SCHEMA, target(), "note", "upper");
    }

    /**
     * Replaces an existing default.
     */
    @Test
    void replacesExistingDefault() {
        setColumnDefault("note", "'first'");
        setColumnDefault("note", "'second'");

        assertColumnDefault(PUBLIC_SCHEMA, target(), "note", "'second'");
    }

    /**
     * Rejects a default with a top-level comma, which could append another
     * ALTER TABLE action, with an invalid-parameter error.
     */
    @Test
    void rejectsTopLevelComma() {
        assertSqlState("22023", () -> setColumnDefault("note", "'a', DROP COLUMN id"));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
    }

    /**
     * A comma inside a function call is not top-level and is accepted.
     */
    @Test
    void acceptsCommaInsideFunctionCall() {
        setColumnDefault("note", "concat('a', 'b')");

        assertColumnDefault(PUBLIC_SCHEMA, target(), "note", "concat");
    }

    /**
     * Rejects null or blank arguments through their domains before the body
     * runs.
     */
    @Test
    void rejectsNullAndBlankArguments() {
        assertDomainViolation(() -> setColumnDefault(null, "'x'"));
        assertDomainViolation(() -> setColumnDefault("note", null));
        assertDomainViolation(() -> setColumnDefault("note", "   "));
        assertDomainViolation(() -> setColumnDefault("note", "'x'", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> setColumnDefault("note", "'x'", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> setColumnDefault("note", "'x'", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    private void setColumnDefault(String column, String defaultValue) {
        setColumnDefault(column, defaultValue, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void setColumnDefault(String column, String defaultValue, Integer lockTimeout,
                                  Integer sleepTime, Integer duration) {
        Routines.setColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, target(), column, defaultValue,
                lockTimeout, sleepTime, duration);
    }
}

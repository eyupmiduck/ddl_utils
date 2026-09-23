package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.drop_expression}: it builds an
 * {@code ALTER COLUMN ... DROP EXPRESSION} fragment that turns a generated
 * column into a plain base column.
 */
class DropExpressionTest extends SingleTableTest {

    DropExpressionTest() {
        super("drop_expression_target", "a int, b int GENERATED ALWAYS AS (a * 2) STORED");
    }

    /**
     * Turns a generated column into a plain column whose value can be written
     * directly.
     */
    @Test
    void dropsExpression() {
        assertTrue(isGenerated(PUBLIC_SCHEMA, target(), "b"));

        dropExpression("b");

        assertFalse(isGenerated(PUBLIC_SCHEMA, target(), "b"));
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (a, b) VALUES (1, 99)");
        Integer value = dsl.fetchOne("SELECT b FROM " + PUBLIC_SCHEMA + "." + target()).get(0, Integer.class);
        assertEquals(99, value);
    }

    /**
     * Dropping the expression of a non-generated column raises the
     * object-not-in-prerequisite-state error from PostgreSQL.
     */
    @Test
    void rejectsNonGeneratedColumn() {
        assertSqlState("55000", () -> dropExpression("a"));
    }

    /**
     * Rejects null arguments through their domains before the body runs.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> dropExpression(null));
        assertDomainViolation(() -> dropExpression("b", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> dropExpression("b", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> dropExpression("b", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    private void dropExpression(String column) {
        dropExpression(column, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void dropExpression(String column, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.dropExpression(dsl.configuration(), PUBLIC_SCHEMA, target(), column,
                lockTimeout, sleepTime, duration);
    }
}

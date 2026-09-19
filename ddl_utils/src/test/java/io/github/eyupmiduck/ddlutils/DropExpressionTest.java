package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.drop_expression}: it builds an
 * {@code ALTER COLUMN ... DROP EXPRESSION} fragment that turns a generated
 * column into a plain base column.
 */
class DropExpressionTest extends PostgresTestBase {

    private static final String TARGET = "drop_expression_target";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "a int, b int GENERATED ALWAYS AS (a * 2) STORED");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Turns a generated column into a plain column whose value can be written
     * directly.
     */
    @Test
    void dropsExpression() {
        assertTrue(isGenerated(PUBLIC_SCHEMA, TARGET, "b"));

        dropExpression("b");

        assertFalse(isGenerated(PUBLIC_SCHEMA, TARGET, "b"));
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (a, b) VALUES (1, 99)");
        Integer value = dsl.fetchOne("SELECT b FROM " + PUBLIC_SCHEMA + "." + TARGET).get(0, Integer.class);
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
        Routines.dropExpression(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column,
                lockTimeout, sleepTime, duration);
    }
}

package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@code ddl_utils.lock_settings_warnings}: healthy timings
 * produce no warnings, and each degenerate combination produces its own
 * explanation without rejecting the zero semantics.
 */
class LockSettingsWarningsTest extends PostgresTestBase {

    /**
     * A normal set of timings has no warnings.
     */
    @Test
    void healthyTimingsHaveNoWarnings() {
        assertEquals("", warnings(100, 500, 30000));
    }

    /**
     * Each zero/degenerate combination adds a specific warning.
     */
    @Test
    void degenerateTimingsAreExplained() {
        assertTrue(warnings(0, 500, 30000).contains("timeout is disabled"));
        assertTrue(warnings(100, 0, 30000).contains("busy-wait"));
        assertTrue(warnings(100, 500, 0).contains("no retry"));
        assertTrue(warnings(500, 500, 300).contains("outlast the retry budget"));
    }

    /**
     * All-zero timings produce every applicable warning and are still accepted
     * by the function.
     */
    @Test
    void allZeroTimingsProduceAllWarnings() {
        String warnings = warnings(0, 0, 0);
        assertTrue(warnings.contains("timeout is disabled"));
        assertTrue(warnings.contains("busy-wait"));
        assertTrue(warnings.contains("no retry"));
    }

    private String warnings(int lockTimeout, int sleepTime, int statementDuration) {
        return evaluate("pg_catalog.array_to_string(ddl_utils.lock_settings_warnings("
                + lockTimeout + ", " + sleepTime + ", " + statementDuration + "), '|')", String.class);
    }
}

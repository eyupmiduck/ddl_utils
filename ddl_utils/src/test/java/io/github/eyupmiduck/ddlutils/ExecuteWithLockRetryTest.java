package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@code ddl_utils_lib.execute_with_lock_retry}: it executes a
 * statement and gives up with 55P03 when a conflicting lock outlasts the retry
 * budget.
 */
class ExecuteWithLockRetryTest extends PostgresTestBase {

    private static final String TABLE = "execute_with_lock_retry_target";

    @BeforeEach
    void createTable() {
        createTestTable(TABLE, "id int");
    }

    @AfterEach
    void dropTable() {
        dropTestTable(TABLE);
    }

    /**
     * A statement with no contention is executed.
     */
    @Test
    void executesAStatement() {
        Routines.executeWithLockRetry(dsl.configuration(),
                "ALTER TABLE " + PUBLIC_SCHEMA + "." + TABLE + " ADD COLUMN added int",
                "test", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        assertTrue(hasColumn(PUBLIC_SCHEMA, TABLE, "added"));
    }

    /**
     * A held conflicting lock makes the runner exhaust its budget and leave the
     * table unchanged.
     */
    @Test
    void givesUpAfterTheLockRetryBudget() throws Exception {
        assertGivesUpWhileTableLocked(TABLE, 2000, () -> Routines.executeWithLockRetry(
                dsl.configuration(),
                "ALTER TABLE " + PUBLIC_SCHEMA + "." + TABLE + " ADD COLUMN blocked int",
                "test", 100, 100, 300));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TABLE, "blocked"));
    }
}

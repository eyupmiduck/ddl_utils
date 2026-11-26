package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
     * A held conflicting lock makes the runner exhaust its budget, leave the
     * table unchanged, and report the attempt count and elapsed time.
     */
    @Test
    void givesUpAfterTheLockRetryBudget() throws Exception {
        runWhileTableLocked(TABLE, 2000, () -> {
            DataAccessException failure = assertThrows(DataAccessException.class,
                    () -> Routines.executeWithLockRetry(
                            dsl.configuration(),
                            "ALTER TABLE " + PUBLIC_SCHEMA + "." + TABLE + " ADD COLUMN blocked int",
                            "test", 100, 100, 300));

            assertEquals("55P03", failure.sqlState());
            assertTrue(failure.getMessage().contains("attempt(s)"),
                    () -> "the error should report the attempt count: " + failure.getMessage());
            assertTrue(failure.getMessage().contains("retry budget 300 ms"),
                    () -> "the error should report the retry budget: " + failure.getMessage());
        });

        assertFalse(hasColumn(PUBLIC_SCHEMA, TABLE, "blocked"));
    }

    /**
     * The runner marks the session in pg_stat_activity while it runs and
     * restores the caller's application_name on success.
     */
    @Test
    void setsAndRestoresApplicationName() throws Exception {
        try (Connection worker = openTestConnection()) {
            executeStatement(worker, "SET application_name = 'original-app'");
            int pid = backendPid(worker);

            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Void> future = pool.submit(() -> {
                    Routines.executeWithLockRetry(
                            DSL.using(worker, SQLDialect.POSTGRES).configuration(),
                            "SELECT pg_catalog.pg_sleep(1)",
                            "marker-test", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
                    return null;
                });

                awaitApplicationName(pid, "ddl_utils:marker-test");
                future.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            assertEquals("original-app", currentApplicationName(worker));
        }
    }

    private void awaitApplicationName(int pid, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var row = dsl.fetchOne(
                    "SELECT application_name FROM pg_catalog.pg_stat_activity WHERE pid = ?", pid);
            String actual = row == null ? null : row.get("application_name", String.class);
            if (expected.equals(actual)) {
                return;
            }
            Thread.sleep(10);
        }
        fail("application_name was not set to " + expected);
    }

    private String currentApplicationName(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT current_setting('application_name')")) {
            result.next();
            return result.getString(1);
        }
    }
}

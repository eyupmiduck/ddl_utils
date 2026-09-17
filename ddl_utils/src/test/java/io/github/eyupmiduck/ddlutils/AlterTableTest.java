package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies {@code ddl_utils.alter_table}: it applies ALTER TABLE fragments to a
 * caller-owned table, retries while another session holds a conflicting lock,
 * gives up once {@code i_statement_duration} is exceeded, and rejects null or
 * invalid inputs through the ddl_utils domains.
 *
 * <p>The function is SECURITY INVOKER, so the test role owns the table it
 * alters.
 */
class AlterTableTest extends PostgresTestBase {

    private static final String TARGET = "alter_table_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Applies an add-column and a drop-column fragment to a caller-owned table
     * and observes the schema change.
     */
    @Test
    void appliesAlterTableFragments() {
        alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN added int", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "added"));

        alterTable(PUBLIC_SCHEMA, TARGET, "DROP COLUMN added", 1000, 10, 5000);
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "added"));
    }

    /**
     * Rejects a fragment that tries to terminate the statement and run another
     * command, leaving the table (and its schema) untouched.
     */
    @Test
    void rejectsFragmentsWithMultipleStatements() {
        assertSqlState("22023", () -> alterTable(
                PUBLIC_SCHEMA, TARGET, "ADD COLUMN injected int; DROP TABLE " + TARGET, 1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "injected"));
        assertDoesNotThrow(() -> dsl.fetch("SELECT 1 FROM " + TARGET));
    }

    /**
     * Retries after a lock timeout and succeeds once a competing session
     * releases its lock. The lock is held before the call and released after
     * it, so success proves at least one retry happened.
     */
    @Test
    void retriesUntilTheLockIsAvailable() throws Exception {
        try (Connection other = openTestConnection()) {
            holdAccessShareLock(other);
            awaitLockHeld();

            CompletableFuture<Void> release = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000);
                    other.rollback();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN retried int", 200, 200, 30000);
            release.join();
        }

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "retried"));
    }

    /**
     * Gives up with a lock-not-available error once the call exceeds
     * {@code i_statement_duration}, leaving the table unaltered.
     */
    @Test
    void givesUpAfterStatementDuration() throws Exception {
        try (Connection other = openTestConnection()) {
            holdAccessShareLock(other);
            awaitLockHeld();

            assertSqlState("55P03", () -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN never int", 100, 100, 300));

            assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "never"));
        }
    }

    /**
     * Rejects null for each text argument through its domain check constraint,
     * before the function body runs.
     */
    @Test
    void rejectsNullTextArguments() {
        assertDomainViolation(() -> alterTable(null, TARGET, "ADD COLUMN x int", 100, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, null, "ADD COLUMN x int", 100, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, TARGET, null, 100, 100, 1000));
    }

    /**
     * Rejects null and negative integer arguments through the domain check
     * constraint, before the function body runs.
     */
    @Test
    void rejectsNullAndNegativeIntegerArguments() {
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN x int", null, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN x int", 100, null, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN x int", 100, 100, null));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN x int", -1, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN x int", 100, -1, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN x int", 100, 100, -1));
    }

    private void alterTable(String schema, String table, String fragment,
                            Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.alterTable(dsl.configuration(), schema, table, fragment,
                lockTimeout, sleepTime, duration);
    }

    private void holdAccessShareLock(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("LOCK TABLE " + TARGET + " IN ACCESS SHARE MODE");
        }
    }

    private void awaitLockHeld() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Object held = dsl.fetchValue(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_locks l
                        JOIN pg_class c ON c.oid = l.relation
                        WHERE c.relname = ? AND l.mode = 'AccessShareLock' AND l.granted
                    )
                    """,
                    TARGET);
            if (Boolean.TRUE.equals(held)) {
                return;
            }
            Thread.sleep(50);
        }
        fail("the competing session did not acquire its lock");
    }
}

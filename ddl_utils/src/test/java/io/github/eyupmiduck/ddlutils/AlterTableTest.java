package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.alter_table}: it applies ALTER TABLE fragments to a
 * caller-owned table, retries while another session holds a conflicting lock,
 * gives up once {@code i_statement_duration} is exceeded, and rejects null or
 * invalid inputs through the ddl_utils domains.
 *
 * <p>The function is SECURITY INVOKER, so the test role owns the table it
 * alters.
 */
class AlterTableTest extends SingleTableTest {

    AlterTableTest() {
        super("alter_table_target", "id int");
    }

    private static void assertCallerLockTimeout(DSLContext tx, String expected) {
        String actual = tx.fetchOne("SELECT current_setting('lock_timeout') AS value")
                .get("value", String.class);
        assertEquals(expected, actual);
    }

    /**
     * Applies an add-column and a drop-column fragment to a caller-owned table
     * and observes the schema change.
     */
    @Test
    void appliesAlterTableFragments() {
        alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN added int", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "added"));

        alterTable(PUBLIC_SCHEMA, target(), "DROP COLUMN added", 1000, 10, 5000);
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "added"));
    }

    /**
     * Rejects a fragment that tries to terminate the statement and run another
     * command, leaving the table (and its schema) untouched.
     */
    @Test
    void rejectsFragmentsWithMultipleStatements() {
        assertSqlState("22023", () -> alterTable(
                PUBLIC_SCHEMA, target(), "ADD COLUMN injected int; DROP TABLE " + target(), 1000, 10, 5000));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "injected"));
        assertDoesNotThrow(() -> dsl.fetchOne("SELECT 1 FROM " + target()));
    }

    /**
     * Accepts fragments whose quoted text contains characters that would only
     * be unsafe outside quotes: a default literal with a semicolon and a quoted
     * identifier with comment markers.
     */
    @Test
    void acceptsQuotedSeparatorsAndComments() {
        alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN note text DEFAULT 'a;b'", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "note"));

        alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN \"x--y\" int", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "x--y"));
    }

    /**
     * Accepts a doubled (escaped) quote with a separator inside the literal,
     * while still rejecting a separator written outside quotes.
     */
    @Test
    void acceptsEscapedQuoteAndRejectsUnquotedSeparator() {
        alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN note text DEFAULT 'it''s;ok'", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "note"));

        assertSqlState("22023", () -> alterTable(
                PUBLIC_SCHEMA, target(), "ADD COLUMN bad int; SELECT 1", 1000, 10, 5000));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "bad"));
    }

    /**
     * Accepts an unquoted identifier containing a dollar sign (legal in
     * PostgreSQL) while still rejecting a comment written outside quotes.
     */
    @Test
    void acceptsDollarIdentifierAndRejectsUnquotedComment() {
        alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN a$b int", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "a$b"));

        assertSqlState("22023", () -> alterTable(
                PUBLIC_SCHEMA, target(), "ADD COLUMN commented int -- trailing", 1000, 10, 5000));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "commented"));
    }

    /**
     * Retries after a lock timeout and succeeds once a competing session
     * releases its lock. The lock is held when the call starts and released on
     * a background thread after a delay, so at least one attempt must have timed
     * out and been retried before the call succeeds.
     */
    @Test
    void retriesUntilTheLockIsAvailable() {
        runWhileTableLocked(target(), 1000,
                () -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN retried int", 200, 200, 30000));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "retried"));
    }

    /**
     * Gives up with a lock-not-available error once the call exceeds
     * {@code i_statement_duration}, leaving the table unaltered.
     */
    @Test
    void givesUpAfterStatementDuration() throws Exception {
        assertGivesUpWhileTableLocked(target(), 2000,
                () -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN never int", 100, 100, 300));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "never"));
    }

    /**
     * A successful call restores the caller's lock_timeout, so it does not leak
     * into the rest of the caller's transaction.
     */
    @Test
    void restoresCallerLockTimeout() {
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            tx.execute("SET LOCAL lock_timeout = '7s'");
            String before = tx.fetchOne("SELECT current_setting('lock_timeout') AS value")
                    .get("value", String.class);

            tx.fetch("SELECT ddl_utils_lib.alter_table(?, ?, ?, ?, ?, ?)",
                    PUBLIC_SCHEMA, target(), "ADD COLUMN tuned int", 1000, 10, 5000);

            String after = tx.fetchOne("SELECT current_setting('lock_timeout') AS value")
                    .get("value", String.class);
            assertEquals(before, after);
        });
    }

    /**
     * Rejects null for each text argument through its domain check constraint,
     * before the function body runs.
     */
    @Test
    void rejectsNullTextArguments() {
        assertDomainViolation(() -> alterTable(null, target(), "ADD COLUMN x int", 100, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, null, "ADD COLUMN x int", 100, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, target(), null, 100, 100, 1000));
    }

    /**
     * Rejects null and negative integer arguments through the domain check
     * constraint, before the function body runs.
     */
    @Test
    void rejectsNullAndNegativeIntegerArguments() {
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN x int", null, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN x int", 100, null, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN x int", 100, 100, null));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN x int", -1, 100, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN x int", 100, -1, 1000));
        assertDomainViolation(() -> alterTable(PUBLIC_SCHEMA, target(), "ADD COLUMN x int", 100, 100, -1));
    }

    /**
     * A failed call also leaves the caller's lock_timeout unchanged: both the
     * validation-rejection path (before the setting is changed) and the
     * lock-exhaustion path (after it is changed, where the aborted savepoint
     * restores it) must not leak the routine's value.
     */
    @Test
    void restoresCallerLockTimeoutOnFailure() throws Exception {
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            tx.execute("SET LOCAL lock_timeout = '7s'");

            try {
                tx.transaction(inner -> DSL.using(inner).fetch(
                        "SELECT ddl_utils_lib.alter_table(?, ?, ?, ?, ?, ?)",
                        PUBLIC_SCHEMA, target(), "ADD COLUMN x int; SELECT 1", 1000, 10, 5000));
                fail("expected the invalid fragment to be rejected");
            } catch (DataAccessException expected) {
                assertEquals("22023", expected.sqlState());
            }
            assertCallerLockTimeout(tx, "7s");

            try (Connection other = openTestConnection()) {
                int holderPid = holdAccessShareLock(other, target());
                awaitAccessShareLockHeld(target(), holderPid);
                try {
                    tx.transaction(inner -> DSL.using(inner).fetch(
                            "SELECT ddl_utils_lib.alter_table(?, ?, ?, ?, ?, ?)",
                            PUBLIC_SCHEMA, target(), "ADD COLUMN never int", 100, 100, 300));
                    fail("expected the lock-exhausted call to give up");
                } catch (DataAccessException expected) {
                    assertEquals("55P03", expected.sqlState());
                }
            }
            assertCallerLockTimeout(tx, "7s");
        });
    }

    private void alterTable(String schema, String table, String fragment,
                            Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.alterTable(dsl.configuration(), schema, table, fragment,
                lockTimeout, sleepTime, duration);
    }

}

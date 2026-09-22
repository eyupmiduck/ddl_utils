package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertDoesNotThrow(() -> dsl.fetchOne("SELECT 1 FROM " + TARGET));
    }

    /**
     * Accepts fragments whose quoted text contains characters that would only
     * be unsafe outside quotes: a default literal with a semicolon and a quoted
     * identifier with comment markers.
     */
    @Test
    void acceptsQuotedSeparatorsAndComments() {
        alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN note text DEFAULT 'a;b'", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "note"));

        alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN \"x--y\" int", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "x--y"));
    }

    /**
     * Accepts an unquoted identifier containing a dollar sign (legal in
     * PostgreSQL) while still rejecting a comment written outside quotes.
     */
    @Test
    void acceptsDollarIdentifierAndRejectsUnquotedComment() {
        alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN a$b int", 1000, 10, 5000);
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "a$b"));

        assertSqlState("22023", () -> alterTable(
                PUBLIC_SCHEMA, TARGET, "ADD COLUMN commented int -- trailing", 1000, 10, 5000));
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "commented"));
    }

    /**
     * Retries after a lock timeout and succeeds once a competing session
     * releases its lock. The lock is held before the call and released after
     * it, so success proves at least one retry happened.
     */
    @Test
    void retriesUntilTheLockIsAvailable() {
        runWhileTableLocked(TARGET, 1000,
                () -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN retried int", 200, 200, 30000));

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "retried"));
    }

    /**
     * Gives up with a lock-not-available error once the call exceeds
     * {@code i_statement_duration}, leaving the table unaltered.
     */
    @Test
    void givesUpAfterStatementDuration() throws Exception {
        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> alterTable(PUBLIC_SCHEMA, TARGET, "ADD COLUMN never int", 100, 100, 300));

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "never"));
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
                    PUBLIC_SCHEMA, TARGET, "ADD COLUMN tuned int", 1000, 10, 5000);

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

}

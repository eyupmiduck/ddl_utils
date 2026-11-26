package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code ddl_utils.ensure_check_constraint} procedure: it adds a
 * CHECK constraint as NOT VALID, commits, then validates it in a second
 * committed step, and recovers when called again after a partial failure.
 */
class EnsureCheckConstraintProcedureTest extends SingleTableTest {

    /**
     * The server-side statement timeout each concurrent worker sets, so a CALL
     * blocked on a lock aborts instead of hanging the build.
     */
    private static final String WORKER_STATEMENT_TIMEOUT = "15s";

    EnsureCheckConstraintProcedureTest() {
        super("ensure_check_constraint_target", "id int, value int");
    }

    /**
     * Adds and validates the constraint, so it is enforced afterwards.
     */
    @Test
    void addsAndValidatesConstraint() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (value) VALUES (1)");

        callEnsureCheckConstraint("positive", "value > 0");

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
        assertSqlState("23514",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (value) VALUES (-1)"));
    }

    /**
     * A same-named constraint of another type is not mistaken for the requested
     * CHECK, so the procedure fails loudly instead of silently skipping it.
     */
    @Test
    void rejectsSameNamedNonCheckConstraint() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD CONSTRAINT positive UNIQUE (value)");

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> callEnsureCheckConstraint("positive", "value > 0"));

        assertEquals("42710", failure.sqlState());
        assertTrue(failure.getMessage().contains("different kind"),
                () -> "the error should name the constraint-kind conflict: " + failure.getMessage());
        assertEquals("u", constraintType(PUBLIC_SCHEMA, "positive"),
                "the pre-existing UNIQUE constraint must not be replaced");
    }

    /**
     * The constraint name is the identity: a same-named CHECK with a different
     * expression is left untouched and the original expression stays enforced.
     */
    @Test
    void keepsOriginalExpressionForSameName() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (value) VALUES (1)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD CONSTRAINT positive CHECK (value > 0)");
        assertValidated(PUBLIC_SCHEMA, target(), "positive");

        callEnsureCheckConstraint("positive", "value >= 100");

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
        assertDoesNotThrow(() -> dsl.execute(
                        "INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (value) VALUES (5)"),
                "the original value > 0 constraint must still accept 5");
    }

    /**
     * A constraint name over PostgreSQL's 63-byte identifier limit is rejected
     * before any DDL, instead of being added under a truncated name that the
     * validation and recovery guards can never find.
     */
    @Test
    void rejectsOverLongConstraintName() {
        String overLong = "c".repeat(64);
        assertSqlState("22023", () -> callEnsureCheckConstraint(overLong, "value > 0"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), overLong.substring(0, 63)),
                "no truncated constraint may be created");

        assertSqlState("22023", () -> callEnsureCheckConstraint("\u00e9".repeat(32), "value > 0"));
    }

    /**
     * Over-long schema and table names are rejected with 22023 before any
     * catalog lookup or DDL; the identifier-domain guard in the resolver is the
     * backstop, but the procedure's own check names the argument.
     */
    @Test
    void rejectsOverLongSchemaAndTableNames() {
        assertSqlState("22023", () -> Routines.ensureCheckConstraint(
                dsl.configuration(), "s".repeat(64), target(), "c", "value > 0"));
        assertSqlState("22023", () -> Routines.ensureCheckConstraint(
                dsl.configuration(), PUBLIC_SCHEMA, "t".repeat(64), "c", "value > 0"));
    }

    /**
     * A view is not a table; the relation-kind guard rejects it before any DDL.
     */
    @Test
    void rejectsNonTableRelation() {
        dsl.execute("CREATE VIEW " + PUBLIC_SCHEMA + ".ensure_check_constraint_view AS SELECT 1 AS value");
        try {
            assertSqlState("42809", () -> Routines.ensureCheckConstraint(dsl.configuration(),
                    PUBLIC_SCHEMA, "ensure_check_constraint_view", "positive", "value > 0"));
        } finally {
            dsl.execute("DROP VIEW IF EXISTS " + PUBLIC_SCHEMA + ".ensure_check_constraint_view");
        }
    }

    /**
     * A second call on an already valid constraint is a no-op.
     */
    @Test
    void isIdempotent() {
        callEnsureCheckConstraint("positive", "value > 0");

        callEnsureCheckConstraint("positive", "value > 0");

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
    }

    /**
     * A call interrupted after the add (constraint present but NOT VALID) is
     * completed by calling again.
     */
    @Test
    void recoversFromPartialFailureAfterAdd() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD CONSTRAINT positive CHECK (value > 0) NOT VALID");
        assertNotValidated(PUBLIC_SCHEMA, target(), "positive");

        callEnsureCheckConstraint("positive", "value > 0");

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
    }

    /**
     * A constraint dropped after the add committed but before validation is
     * re-read by name in step 2, so the call fails as {@code undefined_object}
     * instead of validating a stale OID. A re-run recreates and validates it.
     */
    @Test
    void failsClosedWhenTheConstraintIsDroppedBetweenSteps() throws Exception {
        long key = ensureCheckConstraintLockKey(PUBLIC_SCHEMA, target());

        try (AdvisoryLockStepGate gate = AdvisoryLockStepGate.start(dsl,
                () -> openConnectionWithStatementTimeout(WORKER_STATEMENT_TIMEOUT),
                key,
                connection -> callEnsureCheckConstraint(connection, "positive", "value > 0"))) {
            gate.pauseAfterStep(1);
            assertNotValidated(PUBLIC_SCHEMA, target(), "positive");

            dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target() + " DROP CONSTRAINT positive");
            Throwable failure = gate.resume();

            assertEquals("42704", sqlState(failure),
                    () -> "a constraint dropped between steps must fail as undefined_object: " + failure);
        }

        callEnsureCheckConstraint("positive", "value > 0");

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
    }

    /**
     * A violation by existing rows makes validation fail with a check
     * violation, leaving the constraint NOT VALID; the same call succeeds after
     * the data is fixed.
     */
    @Test
    void failsOnViolatingRowsThenSucceedsAfterFix() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (value) VALUES (-1)");

        assertSqlState("23514", () -> callEnsureCheckConstraint("positive", "value > 0"));
        assertNotValidated(PUBLIC_SCHEMA, target(), "positive");

        dsl.execute("UPDATE " + PUBLIC_SCHEMA + "." + target() + " SET value = 1 WHERE value < 0");
        callEnsureCheckConstraint("positive", "value > 0");

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
    }

    /**
     * Two concurrent calls for the same constraint are serialized by the
     * procedure's transaction-scoped advisory lock, so both succeed instead of
     * one failing with duplicate_object. Each worker runs with a server-side
     * statement timeout, and a worker that does not finish is cancelled rather
     * than hanging the build.
     */
    @Test
    void concurrentCallsAreSerialized() throws Exception {
        int callers = 2;
        CyclicBarrier barrier = new CyclicBarrier(callers);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            tasks.add(() -> {
                try (Connection connection = openConnectionWithStatementTimeout(WORKER_STATEMENT_TIMEOUT)) {
                    barrier.await(10, TimeUnit.SECONDS);
                    Routines.ensureCheckConstraint(
                            DSL.using(connection, SQLDialect.POSTGRES).configuration(),
                            PUBLIC_SCHEMA, target(), "positive", "value > 0");
                }
                return null;
            });
        }

        runConcurrently(callers, tasks);

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
    }

    /**
     * A missing source table is reported as undefined_table with the
     * procedure's own message, not a raw ::regclass cast error.
     */
    @Test
    void rejectsMissingSourceTable() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> Routines.ensureCheckConstraint(dsl.configuration(),
                        PUBLIC_SCHEMA, "ensure_check_constraint_missing", "positive", "value > 0"));

        assertEquals("42P01", failure.sqlState());
        assertTrue(failure.getMessage().contains("ensure_check_constraint: table"),
                () -> "the message should name the missing table: " + failure.getMessage());
    }

    /**
     * Runs the tasks on a fixed pool and waits at most 30 s for each, cancelling
     * every worker on timeout and always shutting the pool down, so a blocked
     * procedure cannot hang the build.
     *
     * @param workers the pool size
     * @param tasks   the worker tasks
     * @throws Exception the first worker failure, or the timeout failure
     */
    private void runConcurrently(int workers, List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    for (Future<Void> pending : futures) {
                        pending.cancel(true);
                    }
                    fail("a concurrent ensure_check_constraint call did not finish within 30 s", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void callEnsureCheckConstraint(String name, String expression) {
        Routines.ensureCheckConstraint(dsl.configuration(),
                PUBLIC_SCHEMA, target(), name, expression);
    }

    private void callEnsureCheckConstraint(Connection connection, String name, String expression) {
        Routines.ensureCheckConstraint(DSL.using(connection, SQLDialect.POSTGRES).configuration(),
                PUBLIC_SCHEMA, target(), name, expression);
    }

}

package io.github.eyupmiduck.ddlutils;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
     * A view is not a table; the relation-kind guard rejects it before any DDL.
     */
    @Test
    void rejectsNonTableRelation() {
        dsl.execute("CREATE VIEW " + PUBLIC_SCHEMA + ".ensure_check_constraint_view AS SELECT 1 AS value");
        try {
            assertSqlState("42809", () -> dsl.execute(
                    "CALL ddl_utils.ensure_check_constraint(?, ?, ?, ?)",
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
                try (Connection connection = openWorkerConnection();
                     Statement statement = connection.createStatement()) {
                    barrier.await(10, TimeUnit.SECONDS);
                    statement.execute("CALL ddl_utils.ensure_check_constraint('"
                            + PUBLIC_SCHEMA + "', '" + target() + "', 'positive', 'value > 0')");
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
        DataAccessException failure = assertThrows(DataAccessException.class, () -> dsl.execute(
                "CALL ddl_utils.ensure_check_constraint(?, ?, ?, ?)",
                PUBLIC_SCHEMA, "ensure_check_constraint_missing", "positive", "value > 0"));

        assertEquals("42P01", failure.sqlState());
        assertTrue(failure.getMessage().contains("ensure_check_constraint: table"),
                () -> "the message should name the missing table: " + failure.getMessage());
    }

    /**
     * Opens a worker connection whose session aborts any statement after
     * {@link #WORKER_STATEMENT_TIMEOUT}: the backstop for a CALL the driver
     * cannot interrupt once it is blocked in a socket read.
     *
     * @return a connection with a server-side statement timeout
     * @throws SQLException if the connection or the setting fails
     */
    private Connection openWorkerConnection() throws SQLException {
        Connection connection = openTestConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET statement_timeout = '" + WORKER_STATEMENT_TIMEOUT + "'");
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
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
        dsl.execute("CALL ddl_utils.ensure_check_constraint(?, ?, ?, ?)",
                PUBLIC_SCHEMA, target(), name, expression);
    }

}

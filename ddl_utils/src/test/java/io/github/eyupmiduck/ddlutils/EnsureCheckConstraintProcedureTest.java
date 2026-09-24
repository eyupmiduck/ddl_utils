package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Verifies the {@code ddl_utils.ensure_check_constraint} procedure: it adds a
 * CHECK constraint as NOT VALID, commits, then validates it in a second
 * committed step, and recovers when called again after a partial failure.
 */
class EnsureCheckConstraintProcedureTest extends SingleTableTest {

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

        assertSqlState("42710", () -> callEnsureCheckConstraint("positive", "value > 0"));
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
     * one failing with duplicate_object.
     */
    @Test
    void concurrentCallsAreSerialized() throws Exception {
        int callers = 2;
        CyclicBarrier barrier = new CyclicBarrier(callers);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try (Connection connection = openTestConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("CALL ddl_utils.ensure_check_constraint('"
                                + PUBLIC_SCHEMA + "', '" + target() + "', 'positive', 'value > 0')");
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertValidated(PUBLIC_SCHEMA, target(), "positive");
    }

    private void callEnsureCheckConstraint(String name, String expression) {
        dsl.execute("CALL ddl_utils.ensure_check_constraint(?, ?, ?, ?)",
                PUBLIC_SCHEMA, target(), name, expression);
    }

}

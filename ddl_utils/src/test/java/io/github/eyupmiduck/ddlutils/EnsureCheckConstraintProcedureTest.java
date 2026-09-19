package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code ddl_utils.ensure_check_constraint} procedure: it adds a
 * CHECK constraint as NOT VALID, commits, then validates it in a second
 * committed step, and recovers when called again after a partial failure.
 */
class EnsureCheckConstraintProcedureTest extends PostgresTestBase {

    private static final String TARGET = "ensure_check_constraint_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, value int");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Adds and validates the constraint, so it is enforced afterwards.
     */
    @Test
    void addsAndValidatesConstraint() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (1)");

        callEnsureCheckConstraint("positive", "value > 0");

        assertTrue(constraintValidated("positive"));
        assertSqlState("23514",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (-1)"));
    }

    /**
     * A same-named constraint of another type is not mistaken for the requested
     * CHECK, so the procedure fails loudly instead of silently skipping it.
     */
    @Test
    void rejectsSameNamedNonCheckConstraint() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
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

        assertTrue(constraintValidated("positive"));
    }

    /**
     * A call interrupted after the add (constraint present but NOT VALID) is
     * completed by calling again.
     */
    @Test
    void recoversFromPartialFailureAfterAdd() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT positive CHECK (value > 0) NOT VALID");
        assertFalse(constraintValidated("positive"));

        callEnsureCheckConstraint("positive", "value > 0");

        assertTrue(constraintValidated("positive"));
    }

    /**
     * A violation by existing rows makes validation fail with a check
     * violation, leaving the constraint NOT VALID; the same call succeeds after
     * the data is fixed.
     */
    @Test
    void failsOnViolatingRowsThenSucceedsAfterFix() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (-1)");

        assertSqlState("23514", () -> callEnsureCheckConstraint("positive", "value > 0"));
        assertFalse(constraintValidated("positive"));

        dsl.execute("UPDATE " + PUBLIC_SCHEMA + "." + TARGET + " SET value = 1 WHERE value < 0");
        callEnsureCheckConstraint("positive", "value > 0");

        assertTrue(constraintValidated("positive"));
    }

    private void callEnsureCheckConstraint(String name, String expression) {
        dsl.execute("CALL ddl_utils.ensure_check_constraint(?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, name, expression);
    }

    private boolean constraintValidated(String name) {
        return dsl.fetchOne(
                """
                        SELECT convalidated
                        FROM pg_constraint
                        WHERE conname = ?
                            AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        """,
                name, PUBLIC_SCHEMA).get(0, Boolean.class);
    }
}

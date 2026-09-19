package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the reusable partial-failure harness ({@link
 * PostgresTestBase#simulateNotNullCheckAdded},
 * {@link PostgresTestBase#simulateNotNullCheckValidated}) across the committing
 * procedures, proving that a call which finds the catalog in any intermediate
 * state finishes the job.
 */
class ProcedureRecoveryTest extends PostgresTestBase {

    private static final String TARGET = "procedure_recovery_target";
    private static final String REFERENCED = "procedure_recovery_referenced";

    @BeforeEach
    void createTargetTables() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(TARGET, "id int, note text, value int, parent_id int");
    }

    @AfterEach
    void cleanUp() {
        dropTestTable(TARGET);
        dropTestTable(REFERENCED);
    }

    /**
     * ensure_not_null completes from the "constraint added, not validated"
     * state (the first committed step).
     */
    @Test
    void ensureNotNullRecoversFromAfterAdd() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, 'a')");
        simulateNotNullCheckAdded(PUBLIC_SCHEMA, TARGET, "note");

        dsl.execute("CALL ddl_utils.ensure_not_null(?, ?, ?)", PUBLIC_SCHEMA, TARGET, "note");

        assertFalse(isNullable("note"));
    }

    /**
     * ensure_not_null completes from the "constraint validated, NOT NULL not
     * set" state (the second committed step).
     */
    @Test
    void ensureNotNullRecoversFromAfterValidate() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, 'a')");
        simulateNotNullCheckValidated(PUBLIC_SCHEMA, TARGET, "note");

        dsl.execute("CALL ddl_utils.ensure_not_null(?, ?, ?)", PUBLIC_SCHEMA, TARGET, "note");

        assertFalse(isNullable("note"));
        assertEquals(0, constraintsNamedLike("%not_null%"));
    }

    /**
     * ensure_check_constraint completes from the "added, not validated" state.
     */
    @Test
    void ensureCheckConstraintRecoversFromAfterAdd() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT value_positive CHECK (value > 0) NOT VALID");
        assertFalse(constraintValidated("value_positive"));

        dsl.execute("CALL ddl_utils.ensure_check_constraint(?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, "value_positive", "value > 0");

        assertTrue(constraintValidated("value_positive"));
    }

    /**
     * ensure_foreign_key completes from the "added, not validated" state.
     */
    @Test
    void ensureForeignKeyRecoversFromAfterAdd() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) NOT VALID");
        assertFalse(constraintValidated("fk_parent"));

        dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, "fk_parent",
                new String[]{"parent_id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id"});

        assertTrue(constraintValidated("fk_parent"));
    }

    private boolean isNullable(String column) {
        return "YES".equals(columnAttribute(PUBLIC_SCHEMA, TARGET, column, "is_nullable"));
    }

    private int constraintsNamedLike(String pattern) {
        // PostgreSQL 18+ records the column's NOT NULL as a pg_constraint row
        // (contype 'n'), whose generated name also ends in _not_null; only the
        // temporary CHECK constraint is of interest here.
        Integer count = dsl.fetchOne(
                """
                        SELECT count(*)::int
                        FROM pg_constraint
                        WHERE conrelid = (SELECT oid FROM pg_class WHERE relname = ?
                                            AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?))
                            AND contype = 'c'
                            AND conname LIKE ?
                        """,
                TARGET, PUBLIC_SCHEMA, pattern).get(0, Integer.class);
        return count != null ? count : -1;
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

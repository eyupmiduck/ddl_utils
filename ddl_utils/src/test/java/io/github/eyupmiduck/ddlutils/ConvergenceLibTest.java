package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for the explicit-settings convergence functions in
 * {@code ddl_utils_lib}; the lock-aware wrappers are covered separately in
 * {@link ConvergenceTest}.
 */
class ConvergenceLibTest extends PostgresTestBase {

    private static final String TARGET = "convergence_lib_target";
    private static final String REFERENCED = "convergence_lib_referenced";

    @BeforeEach
    void createTables() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(TARGET, "id int NOT NULL, note text, parent_id int");
    }

    @AfterEach
    void dropTables() {
        dropTestTable(TARGET);
        dropTestTable(REFERENCED);
    }

    /**
     * The lib function drops an existing column and is a no-op on a second
     * call.
     */
    @Test
    void dropColumnIfExistsDropsAndIsIdempotent() {
        Routines.dropColumnIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "note"));

        Routines.dropColumnIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * The lib function still requires the relation to exist.
     */
    @Test
    void dropColumnIfExistsRejectsMissingTable() {
        assertSqlState("42P01", () -> Routines.dropColumnIfExists(
                dsl.configuration(), PUBLIC_SCHEMA, "no_such_table", "note",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }

    /**
     * The lib function drops a constraint and is a no-op on a second call.
     */
    @Test
    void dropConstraintIfExistsDropsAndIsIdempotent() {
        Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_present",
                "note IS NOT NULL", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);

        Routines.dropConstraintIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_present",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "note_present"));

        Routines.dropConstraintIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_present",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "note_present"));
    }

    /**
     * A dependent foreign key still fails the lib drop with 2BP01.
     */
    @Test
    void dropConstraintIfExistsKeepsRestrictSemantics() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) NOT VALID");

        assertSqlState("2BP01", () -> Routines.dropConstraintIfExists(
                dsl.configuration(), PUBLIC_SCHEMA, REFERENCED, REFERENCED + "_pkey",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertTrue(constraintExists(PUBLIC_SCHEMA, REFERENCED, REFERENCED + "_pkey"));
    }

    /**
     * The lib function attaches a primary key and no-ops on a second call.
     */
    @Test
    void addPrimaryKeyUsingIndexIfAbsentAttachesAndIsIdempotent() {
        dsl.execute("CREATE UNIQUE INDEX target_id_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (id)");

        Routines.addPrimaryKeyUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "target_pkey", "target_id_idx",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertValidated(PUBLIC_SCHEMA, TARGET, "target_pkey");

        Routines.addPrimaryKeyUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "target_pkey", "target_id_idx",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertValidated(PUBLIC_SCHEMA, TARGET, "target_pkey");
    }

    /**
     * A same-named unique constraint is the wrong kind for the primary-key
     * helper.
     */
    @Test
    void addPrimaryKeyUsingIndexIfAbsentRejectsDifferentKind() {
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT target_pkey UNIQUE (id)");

        assertSqlState("42710", () -> Routines.addPrimaryKeyUsingIndexIfAbsent(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "target_pkey", "target_note_idx",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }

    /**
     * The lib function attaches a unique constraint and no-ops on a second
     * call.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentAttachesAndIsIdempotent() {
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");

        Routines.addUniqueConstraintUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "note_uq", "target_note_idx",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertValidated(PUBLIC_SCHEMA, TARGET, "note_uq");

        Routines.addUniqueConstraintUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "note_uq", "target_note_idx",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertValidated(PUBLIC_SCHEMA, TARGET, "note_uq");
    }

    /**
     * The lib function names an index that already backs another constraint
     * instead of letting ADD CONSTRAINT fail generically.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentRejectsIndexBackingAnotherConstraint() {
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT note_uq UNIQUE USING INDEX target_note_idx");

        assertSqlState("42710", () -> Routines.addUniqueConstraintUsingIndexIfAbsent(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_other", "note_uq",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertTrue(constraintExists(PUBLIC_SCHEMA, TARGET, "note_uq"));
    }

    /**
     * A non-unique index is rejected by the lib function's index validation.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentRejectsNonUniqueIndex() {
        dsl.execute("CREATE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");

        assertSqlState("42809", () -> Routines.addUniqueConstraintUsingIndexIfAbsent(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_uq", "target_note_idx",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }
}

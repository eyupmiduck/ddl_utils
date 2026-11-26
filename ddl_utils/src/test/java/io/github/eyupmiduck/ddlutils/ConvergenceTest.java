package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies the idempotent convergence functions: destructive drops are no-ops
 * when the object is absent, index attachment is a no-op when the matching
 * constraint already exists, and mismatched constraints or indexes fail
 * instead of being silently accepted.
 */
class ConvergenceTest extends PostgresTestBase {

    private static final String TARGET = "convergence_target";
    private static final String REFERENCED = "convergence_referenced";
    private static final String WORKER_STATEMENT_TIMEOUT = "15s";

    @BeforeEach
    void createTables() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(TARGET, "id int NOT NULL, note text, parent_id int");
    }

    @AfterEach
    void dropTables() {
        // The target is dropped first so a leftover foreign key cannot block it.
        dropTestTable(TARGET);
        dropTestTable(REFERENCED);
    }

    /**
     * A missing column is a no-op, and an existing column is dropped; a second
     * call is also a no-op.
     */
    @Test
    void dropColumnIfExistsDropsAndIsIdempotent() {
        Routines.dropColumnIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note");
        assertFalseColumn("note");

        Routines.dropColumnIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note");
        assertFalseColumn("note");
    }

    /**
     * The table must exist, so a typo in the table name is not silently
     * treated as a missing column.
     */
    @Test
    void dropColumnIfExistsRejectsMissingTable() {
        assertSqlState("42P01", () -> Routines.dropColumnIfExists(
                dsl.configuration(), PUBLIC_SCHEMA, "no_such_table", "note"));
    }

    /**
     * The wrapper resolves the table's lock settings; a held conflicting lock
     * makes the drop give up on the table budget.
     */
    @Test
    void dropColumnIfExistsUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);
        try {
            assertGivesUpWhileTableLocked(TARGET, 2000,
                    () -> Routines.dropColumnIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note"));
        } finally {
            clearTableLockSettings(PUBLIC_SCHEMA, TARGET);
        }
    }

    /**
     * A missing constraint is a no-op, and an existing constraint is dropped;
     * a second call is also a no-op.
     */
    @Test
    void dropConstraintIfExistsDropsAndIsIdempotent() {
        Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_present",
                "note IS NOT NULL");

        Routines.dropConstraintIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_present");
        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "note_present"));

        Routines.dropConstraintIfExists(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_present");
        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "note_present"));
    }

    /**
     * A primary key still referenced by a foreign key fails with 2BP01; after
     * the caller drops the foreign key, the same call succeeds.
     */
    @Test
    void dropConstraintIfExistsKeepsRestrictSemantics() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) NOT VALID");

        assertSqlState("2BP01", () -> Routines.dropConstraintIfExists(
                dsl.configuration(), PUBLIC_SCHEMA, REFERENCED, REFERENCED + "_pkey"));
        assertTrue(constraintExists(PUBLIC_SCHEMA, REFERENCED, REFERENCED + "_pkey"),
                "the dependent key must remain in place");

        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET + " DROP CONSTRAINT fk_parent");
        Routines.dropConstraintIfExists(dsl.configuration(), PUBLIC_SCHEMA, REFERENCED,
                REFERENCED + "_pkey");
        assertFalse(constraintExists(PUBLIC_SCHEMA, REFERENCED, REFERENCED + "_pkey"));
    }

    /**
     * Attaching a primary key from a valid unique index succeeds and a second
     * call is a no-op.
     */
    @Test
    void addPrimaryKeyUsingIndexIfAbsentAttachesAndIsIdempotent() {
        dsl.execute("CREATE UNIQUE INDEX target_id_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (id)");

        Routines.addPrimaryKeyUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "target_pkey", "target_id_idx");
        assertValidated(PUBLIC_SCHEMA, TARGET, "target_pkey");

        Routines.addPrimaryKeyUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "target_pkey", "target_id_idx");
        assertValidated(PUBLIC_SCHEMA, TARGET, "target_pkey");
    }

    /**
     * A same-named unique constraint is a different kind, not the requested
     * primary key, and is left untouched.
     */
    @Test
    void addPrimaryKeyUsingIndexIfAbsentRejectsDifferentKind() {
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT target_pkey UNIQUE (id)");

        assertSqlState("42710", () -> Routines.addPrimaryKeyUsingIndexIfAbsent(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "target_pkey", "target_note_idx"));
        assertTrue(constraintExists(PUBLIC_SCHEMA, TARGET, "target_pkey"),
                "the existing unique constraint must remain untouched");
    }

    /**
     * Attaching a unique constraint from a valid unique index succeeds and a
     * second call is a no-op.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentAttachesAndIsIdempotent() {
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");

        Routines.addUniqueConstraintUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "note_uq", "target_note_idx");
        assertValidated(PUBLIC_SCHEMA, TARGET, "note_uq");

        Routines.addUniqueConstraintUsingIndexIfAbsent(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "note_uq", "target_note_idx");
        assertValidated(PUBLIC_SCHEMA, TARGET, "note_uq");
    }

    /**
     * A unique index already backing another constraint is reported by name
     * rather than left for ADD CONSTRAINT's generic duplicate error.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentRejectsIndexBackingAnotherConstraint() {
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT note_uq UNIQUE USING INDEX target_note_idx");

        // USING INDEX renames the index to the constraint name.
        assertSqlState("42710", () -> Routines.addUniqueConstraintUsingIndexIfAbsent(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_other", "note_uq"));
        assertTrue(constraintExists(PUBLIC_SCHEMA, TARGET, "note_uq"),
                "the existing unique constraint must remain untouched");
    }

    /**
     * A primary key is not a unique constraint for convergence purposes: a
     * same-named primary key is rejected by the unique helper.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentRejectsDifferentKind() {
        dsl.execute("CREATE UNIQUE INDEX target_id_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (id)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT target_pkey PRIMARY KEY (id)");

        assertSqlState("42710", () -> Routines.addUniqueConstraintUsingIndexIfAbsent(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "target_pkey", "target_id_idx"));
    }

    /**
     * A non-unique index is not attachable and fails through the existing index
     * validation.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentRejectsNonUniqueIndex() {
        dsl.execute("CREATE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");

        assertSqlState("42809", () -> Routines.addUniqueConstraintUsingIndexIfAbsent(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note_uq", "target_note_idx"));
    }

    /**
     * Two sessions attaching the same unique constraint converge: the advisory
     * lock serializes the check-and-attach, and the second call becomes a no-op.
     */
    @Test
    void addUniqueConstraintUsingIndexIfAbsentConcurrentCallsConverge() throws Exception {
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (note)");

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<Void>> tasks = new ArrayList<>();
        tasks.add(() -> callAddUniqueConcurrently(barrier));
        tasks.add(() -> callAddUniqueConcurrently(barrier));

        runConcurrently(tasks);

        assertValidated(PUBLIC_SCHEMA, TARGET, "note_uq");
    }

    private Void callAddUniqueConcurrently(CyclicBarrier barrier) throws Exception {
        try (Connection connection = openConnectionWithStatementTimeout(WORKER_STATEMENT_TIMEOUT)) {
            barrier.await(10, TimeUnit.SECONDS);
            Routines.addUniqueConstraintUsingIndexIfAbsent(
                    DSL.using(connection, SQLDialect.POSTGRES).configuration(),
                    PUBLIC_SCHEMA, TARGET, "note_uq", "target_note_idx");
        }
        return null;
    }

    private static void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
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
                    fail("a concurrent convergence call did not finish within 30 s", e);
                } catch (ExecutionException e) {
                    fail("a concurrent convergence call failed: " + e.getCause(), e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertFalseColumn(String column) {
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, column),
                () -> "column " + column + " should be absent");
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code ddl_utils.ensure_foreign_key} procedure: it adds a foreign
 * key as NOT VALID, commits, then validates it in a second committed step, and
 * recovers when called again after a partial failure.
 */
class EnsureForeignKeyProcedureTest extends PostgresTestBase {

    private static final String TARGET = "ensure_foreign_key_target";
    private static final String REFERENCED = "ensure_foreign_key_referenced";

    /**
     * The server-side statement timeout each concurrent worker sets, so a CALL
     * blocked on a lock aborts instead of hanging the build.
     */
    private static final String WORKER_STATEMENT_TIMEOUT = "15s";

    @BeforeEach
    void createTargetTables() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(TARGET, "id int, parent_id int");
    }

    @AfterEach
    void dropTargetTables() {
        dropTestTable(TARGET);
        dropTestTable(REFERENCED);
    }

    /**
     * Adds and validates the foreign key, so it is enforced afterwards.
     */
    @Test
    void addsAndValidatesForeignKey() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + REFERENCED + " (id) VALUES (1)");

        callEnsureForeignKey("fk_parent");

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
        assertForeignKeyDefinition("fk_parent");
        assertSqlState("23503",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)"));
    }

    /**
     * A self-referencing key exercises the single-table branch of
     * {@code lock_foreign_key_tables}: the add step requests SHARE ROW
     * EXCLUSIVE on both sides, and the validate step requests SHARE UPDATE
     * EXCLUSIVE on the source plus SHARE ROW EXCLUSIVE on the referenced side,
     * which must escalate to one SHARE ROW EXCLUSIVE lock on the same table.
     */
    @Test
    void addsAndValidatesSelfReferencingForeignKey() {
        String self = "ensure_foreign_key_self";
        createTestTable(self, "id int PRIMARY KEY, parent_id int");
        try {
            Routines.ensureForeignKey(dsl.configuration(),
                    PUBLIC_SCHEMA, self, "fk_self", new String[]{"parent_id"},
                    PUBLIC_SCHEMA, self, new String[]{"id"});

            assertValidated(PUBLIC_SCHEMA, self, "fk_self");
        } finally {
            dropTestTable(self);
        }
    }

    /**
     * The add step honours the referenced table's lock settings: with the
     * referenced table's budget far below the database default, an ACCESS
     * EXCLUSIVE lock on it makes the CALL give up quickly even though the
     * source table still uses the default settings.
     */
    @Test
    void addStepUsesReferencedTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, REFERENCED, 100, 100, 300);
        try {
            assertGivesUpWhileTableLocked(REFERENCED, "ACCESS EXCLUSIVE", 2000,
                    () -> callEnsureForeignKey("fk_parent"));

            assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "fk_parent"),
                    "a call that gave up must not have created the foreign key");
        } finally {
            clearTableLockSettings(PUBLIC_SCHEMA, REFERENCED);
        }
    }

    /**
     * The created foreign key has the requested source/referenced tables and
     * column mapping and the PostgreSQL-default referential actions and
     * deferrability, not just any constraint named {@code fk_parent}. The
     * catalog lookups are scoped to the target table and to foreign keys, so a
     * same-named constraint on another table cannot be read instead.
     */
    private void assertForeignKeyDefinition(String name) {
        Record definition = dsl.fetchOne("""
                SELECT c.relname AS table_name,
                       cr.relname AS referenced_table_name,
                       con.confupdtype::text AS update_action,
                       con.confdeltype::text AS delete_action,
                       con.confmatchtype::text AS match_type,
                       con.condeferrable,
                       con.condeferred
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_class cr ON cr.oid = con.confrelid
                JOIN pg_namespace n ON n.oid = con.connamespace
                WHERE con.conname = ?
                    AND con.contype = 'f'
                    AND con.conrelid = (SELECT oid FROM pg_class
                                        WHERE relname = ? AND relnamespace =
                                            (SELECT oid FROM pg_namespace WHERE nspname = ?))
                """, name, TARGET, PUBLIC_SCHEMA);
        assertNotNull(definition, () -> "constraint not found: " + name);
        assertEquals(TARGET, definition.get("table_name", String.class));
        assertEquals(REFERENCED, definition.get("referenced_table_name", String.class));
        assertEquals("a", definition.get("update_action", String.class), "NO ACTION update");
        assertEquals("a", definition.get("delete_action", String.class), "NO ACTION delete");
        assertEquals("s", definition.get("match_type", String.class), "MATCH SIMPLE");
        assertFalse(definition.get("condeferrable", Boolean.class));
        assertFalse(definition.get("condeferred", Boolean.class));

        List<String> mapping = dsl.fetch("""
                SELECT sa.attname || '->' || ra.attname
                FROM pg_constraint con
                JOIN generate_subscripts(con.conkey, 1) AS s(i) ON true
                JOIN pg_attribute sa ON sa.attrelid = con.conrelid AND sa.attnum = con.conkey[s.i]
                JOIN pg_attribute ra ON ra.attrelid = con.confrelid AND ra.attnum = con.confkey[s.i]
                WHERE con.conname = ?
                    AND con.contype = 'f'
                    AND con.conrelid = (SELECT oid FROM pg_class
                                        WHERE relname = ? AND relnamespace =
                                            (SELECT oid FROM pg_namespace WHERE nspname = ?))
                ORDER BY s.i
                """, name, TARGET, PUBLIC_SCHEMA).getValues(0, String.class);
        assertEquals(List.of("parent_id->id"), mapping);
    }

    /**
     * The catalog assertions read the constraint on the target table even when
     * another table in the same schema has a same-named foreign key, so the
     * helper cannot match the wrong rows or fail with a cardinality error.
     */
    @Test
    void readsDefinitionWhenSameNamedForeignKeyExistsElsewhere() {
        String other = "ensure_foreign_key_other";
        createTestTable(other, "id int PRIMARY KEY, parent_id int");
        try {
            dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + other
                    + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                    + PUBLIC_SCHEMA + "." + other + " (id)");

            callEnsureForeignKey("fk_parent");

            assertForeignKeyDefinition("fk_parent");
        } finally {
            dropTestTable(other);
        }
    }

    /**
     * A same-named constraint of another type is not mistaken for the requested
     * foreign key, so the procedure fails loudly instead of silently skipping it.
     */
    @Test
    void rejectsSameNamedNonForeignKeyConstraint() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent UNIQUE (parent_id)");

        assertSqlState("42710", () -> callEnsureForeignKey("fk_parent"));

        assertTrue(constraintExists(PUBLIC_SCHEMA, TARGET, "fk_parent"),
                "the pre-existing constraint must be left in place");
        assertEquals("u", constraintType(PUBLIC_SCHEMA, "fk_parent"),
                "the pre-existing UNIQUE constraint must not be replaced");
    }

    /**
     * Reciprocal foreign keys on two tables serialize on the same advisory-lock
     * slot, so concurrent calls do not deadlock in opposite table-lock order.
     * Each worker runs with a server-side statement timeout and the test fails
     * (cancelling every worker) rather than hanging if one does not finish.
     */
    @Test
    void reciprocalCallsDoNotDeadlock() throws Exception {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + REFERENCED + " ADD COLUMN target_id int");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET + " ADD CONSTRAINT target_id_key UNIQUE (id)");

        try {
            int callers = 2;
            CyclicBarrier barrier = new CyclicBarrier(callers);
            List<Callable<Void>> tasks = new ArrayList<>();
            tasks.add(() -> {
                try (Connection connection = openConnectionWithStatementTimeout(WORKER_STATEMENT_TIMEOUT)) {
                    barrier.await(10, TimeUnit.SECONDS);
                    Routines.ensureForeignKey(
                            DSL.using(connection, SQLDialect.POSTGRES).configuration(),
                            PUBLIC_SCHEMA, TARGET, "fk_forward", new String[]{"parent_id"},
                            PUBLIC_SCHEMA, REFERENCED, new String[]{"id"});
                }
                return null;
            });
            tasks.add(() -> {
                try (Connection connection = openConnectionWithStatementTimeout(WORKER_STATEMENT_TIMEOUT)) {
                    barrier.await(10, TimeUnit.SECONDS);
                    Routines.ensureForeignKey(
                            DSL.using(connection, SQLDialect.POSTGRES).configuration(),
                            PUBLIC_SCHEMA, REFERENCED, "fk_reverse", new String[]{"target_id"},
                            PUBLIC_SCHEMA, TARGET, new String[]{"id"});
                }
                return null;
            });

            runConcurrently(callers, tasks);

            assertValidated(PUBLIC_SCHEMA, TARGET, "fk_forward");
            assertValidated(PUBLIC_SCHEMA, REFERENCED, "fk_reverse");
        } finally {
            // TARGET is dropped before REFERENCED by the class teardown, so the
            // reverse key must not be left depending on TARGET.
            dsl.execute("ALTER TABLE IF EXISTS " + PUBLIC_SCHEMA + "." + REFERENCED
                    + " DROP CONSTRAINT IF EXISTS fk_reverse");
        }
    }

    /**
     * A same-named foreign key with a different definition (here, a different
     * referencing column) is not mistaken for the requested one: the procedure
     * fails loudly instead of silently accepting the wrong constraint. The
     * message is checked case-insensitively (not against the deparse's upper
     * case) and the untouched definition is verified through the catalog.
     */
    @Test
    void rejectsSameNamedForeignKeyWithDifferentDefinition() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id)");

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> callEnsureForeignKey("fk_parent"));

        assertEquals("42710", failure.sqlState());
        assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("different definition"),
                () -> "the mismatch message should say the definition differs: " + failure.getMessage());
        assertEquals("{1}", foreignKeyRow("fk_parent").get("source_key", String.class),
                "the pre-existing key must still reference id, not parent_id");
    }

    /**
     * A same-named foreign key with the same columns but a different referential
     * action is a mismatch, not the target, and is left untouched.
     */
    @Test
    void rejectsSameNamedForeignKeyWithDifferentReferentialAction() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) ON DELETE CASCADE");

        assertSqlState("42710", () -> callEnsureForeignKey("fk_parent"));

        assertEquals("c", foreignKeyRow("fk_parent").get("delete_action", String.class),
                "the pre-existing ON DELETE CASCADE action must be left untouched");
    }

    /**
     * An equivalent foreign key whose columns were declared in a different order
     * than the stored conkey/confkey is recognised as the same definition rather
     * than reported as a mismatch.
     *
     * <p>The stored key is asserted to be stored as (confkey = {2,1}) so the test
     * really exercises the reordering case.
     */
    @Test
    void acceptsSameNamedForeignKeyDeclaredInDifferentColumnOrder() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + REFERENCED + " ADD COLUMN code int");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + REFERENCED
                + " ADD CONSTRAINT ref_code_id UNIQUE (code, id)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (id, parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (code, id)");

        assertEquals("{2,1}", foreignKeyRow("fk_parent").get("referenced_key", String.class));

        Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk_parent",
                new String[]{"parent_id", "id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id", "code"});

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
    }

    /**
     * Referencing and referenced column lists of different lengths are rejected
     * up front rather than silently zipped (which would drop the surplus element
     * and could accept an under-specified definition).
     */
    @Test
    void rejectsMismatchedColumnListLengths() {
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"parent_id", "id"},
                PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));
    }

    /**
     * A repeated column in either list is rejected, since a foreign key's column
     * lists must name distinct columns.
     */
    @Test
    void rejectsDuplicateColumnNames() {
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk_parent",
                new String[]{"parent_id", "parent_id"}, PUBLIC_SCHEMA, REFERENCED,
                new String[]{"id", "id"}));
    }

    /**
     * A same-named foreign key is a mismatch when its match type, update action
     * or deferrability differs from the PostgreSQL defaults.
     */
    @Test
    void rejectsSameNamedForeignKeyWithDifferentMatchUpdateOrDeferrability() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_match FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) MATCH FULL");
        assertSqlState("42710", () -> callEnsureForeignKey("fk_match"));

        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_update FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) ON UPDATE CASCADE");
        assertSqlState("42710", () -> callEnsureForeignKey("fk_update"));

        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_defer FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) DEFERRABLE INITIALLY DEFERRED");
        assertSqlState("42710", () -> callEnsureForeignKey("fk_defer"));
    }

    /**
     * A second call on an already valid constraint is a no-op: the constraint's
     * catalog OID is unchanged, so a drop-and-re-add cannot pass as idempotent.
     */
    @Test
    void isIdempotent() {
        callEnsureForeignKey("fk_parent");
        Long oid = foreignKeyRow("fk_parent").get("oid", Long.class);

        callEnsureForeignKey("fk_parent");

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
        assertEquals(oid, foreignKeyRow("fk_parent").get("oid", Long.class),
                "the second call must validate the existing key, not drop and re-add it");
    }

    /**
     * A source column renamed after the add committed but before validation
     * makes the stored definition (which now uses the new name) differ from the
     * caller's original names. Step 2 re-resolves the relation and fails closed
     * with a definition mismatch instead of validating blindly; a re-run with
     * the new name completes.
     */
    @Test
    void failsClosedWhenASourceColumnIsRenamedBetweenSteps() throws Exception {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + REFERENCED + " (id) VALUES (1)");
        long key = ensureForeignKeyLockKey(PUBLIC_SCHEMA, TARGET, PUBLIC_SCHEMA, REFERENCED);

        try (AdvisoryLockStepGate gate = AdvisoryLockStepGate.start(dsl,
                () -> openConnectionWithStatementTimeout(WORKER_STATEMENT_TIMEOUT),
                key,
                connection -> callEnsureForeignKey(connection, "fk_parent", "parent_id"))) {
            gate.pauseAfterStep(1);
            assertNotValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");

            dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                    + " RENAME COLUMN parent_id TO parent_id_renamed");
            Throwable failure = gate.resume();

            assertEquals("42710", sqlState(failure),
                    () -> "a renamed source column must fail closed with a definition mismatch: " + failure);
            assertNotValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
        }

        callEnsureForeignKey("fk_parent", "parent_id_renamed");

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
    }

    /**
     * A call interrupted after the add (constraint present but NOT VALID) is
     * completed by calling again.
     */
    @Test
    void recoversFromPartialFailureAfterAdd() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) NOT VALID");
        assertNotValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");

        callEnsureForeignKey("fk_parent");

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
    }

    /**
     * A constraint name over PostgreSQL's 63-byte identifier limit is rejected
     * before any DDL, instead of being added under a truncated name that the
     * validation and recovery guards can never find.
     */
    @Test
    void rejectsOverLongConstraintName() {
        String overLong = "c".repeat(64);
        assertSqlState("22023", () -> callEnsureForeignKey(overLong));
        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, overLong.substring(0, 63)),
                "no truncated constraint may be created");

        assertSqlState("22023", () -> callEnsureForeignKey("\u00e9".repeat(32)));
    }

    /**
     * Over-long schema, table and column names are rejected with 22023 before
     * any catalog lookup or DDL, naming the offending argument.
     */
    @Test
    void rejectsOverLongSchemaTableAndColumnNames() {
        String tooLong = "c".repeat(64);
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                tooLong, TARGET, "fk", new String[]{"parent_id"},
                PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, tooLong, "fk", new String[]{"parent_id"},
                PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk", new String[]{"parent_id"},
                tooLong, REFERENCED, new String[]{"id"}));
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk", new String[]{"parent_id"},
                PUBLIC_SCHEMA, tooLong, new String[]{"id"}));
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk", new String[]{tooLong},
                PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));
        assertSqlState("22023", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk", new String[]{"parent_id"},
                PUBLIC_SCHEMA, REFERENCED, new String[]{tooLong}));
    }

    /**
     * The source and referenced relations must be tables; a view is rejected
     * with 42809 before any DDL.
     */
    @Test
    void rejectsNonTableSourceAndReferencedRelations() {
        dsl.execute("CREATE VIEW " + PUBLIC_SCHEMA + ".ensure_foreign_key_view AS SELECT 1 AS id");
        try {
            assertSqlState("42809", () -> Routines.ensureForeignKey(dsl.configuration(),
                    PUBLIC_SCHEMA, "ensure_foreign_key_view", "fk_view", new String[]{"id"},
                    PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));

            assertSqlState("42809", () -> Routines.ensureForeignKey(dsl.configuration(),
                    PUBLIC_SCHEMA, TARGET, "fk_view", new String[]{"parent_id"},
                    PUBLIC_SCHEMA, "ensure_foreign_key_view", new String[]{"id"}));
        } finally {
            dsl.execute("DROP VIEW IF EXISTS " + PUBLIC_SCHEMA + ".ensure_foreign_key_view");
        }
    }

    /**
     * An orphan row makes validation fail with a foreign-key violation, leaving
     * the constraint NOT VALID; the same call succeeds after the data is fixed.
     */
    @Test
    void failsOnOrphanRowThenSucceedsAfterFix() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)");

        assertSqlState("23503", () -> callEnsureForeignKey("fk_parent"));
        assertNotValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");

        dsl.execute("UPDATE " + PUBLIC_SCHEMA + "." + TARGET + " SET parent_id = NULL WHERE parent_id = 999");
        callEnsureForeignKey("fk_parent");

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
    }

    /**
     * An unknown source column is reported as undefined_column even when a
     * same-named foreign key exists, instead of being dropped from the
     * definition check and reported as a mismatch.
     */
    @Test
    void rejectsUnknownSourceColumn() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id)");

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> Routines.ensureForeignKey(dsl.configuration(),
                        PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"missing"}, PUBLIC_SCHEMA, REFERENCED,
                        new String[]{"id"}));

        assertEquals("42703", failure.sqlState());
        assertTrue(failure.getMessage().contains("missing"),
                () -> "the message should name the missing column: " + failure.getMessage());
    }

    /**
     * An unknown referenced column is reported as undefined_column even when a
     * same-named foreign key exists, instead of being reported as a mismatch.
     */
    @Test
    void rejectsUnknownReferencedColumn() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id)");

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> Routines.ensureForeignKey(dsl.configuration(),
                        PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"parent_id"},
                        PUBLIC_SCHEMA, REFERENCED, new String[]{"missing"}));

        assertEquals("42703", failure.sqlState());
        assertTrue(failure.getMessage().contains("missing"),
                () -> "the message should name the missing column: " + failure.getMessage());
    }

    /**
     * A referenced table that does not exist is rejected with undefined_table.
     */
    @Test
    void rejectsUnknownReferencedTable() {
        assertSqlState("42P01", () -> Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"parent_id"}, PUBLIC_SCHEMA, "no_such_table",
                new String[]{"id"}));
    }

    /**
     * A missing source table is reported as undefined_table with the
     * procedure's own message, not a raw ::regclass cast error.
     */
    @Test
    void rejectsMissingSourceTable() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> Routines.ensureForeignKey(dsl.configuration(),
                        PUBLIC_SCHEMA, "ensure_foreign_key_missing", "fk_missing",
                        new String[]{"parent_id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));

        assertEquals("42P01", failure.sqlState());
        assertTrue(failure.getMessage().contains("ensure_foreign_key: table"),
                () -> "the message should name the missing table: " + failure.getMessage());
    }

    /**
     * The referenced columns must be covered by a unique index or constraint even
     * for a NOT VALID foreign key; the procedure reports it before any DDL.
     */
    @Test
    void rejectsReferencedColumnsWithoutUniqueIndex() {
        String noKey = "ensure_foreign_key_no_key";
        createTestTable(noKey, "id int");
        try {
            assertSqlState("42830", () -> Routines.ensureForeignKey(dsl.configuration(),
                    PUBLIC_SCHEMA, TARGET, "fk_no_key", new String[]{"parent_id"},
                    PUBLIC_SCHEMA, noKey, new String[]{"id"}));
            assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "fk_no_key"));
        } finally {
            dropTestTable(noKey);
        }
    }

    /**
     * Returns the catalog row for the named foreign key on the target table,
     * scoped to that table and to {@code contype = 'f'}, so a same-named
     * constraint elsewhere cannot be read instead. A missing row fails with a
     * clear assertion rather than a null dereference.
     *
     * @param name the constraint name
     * @return the constraint row
     */
    private Record foreignKeyRow(String name) {
        Record row = dsl.fetchOne("""
                SELECT con.oid,
                       con.conkey::text AS source_key,
                       con.confkey::text AS referenced_key,
                       con.confdeltype::text AS delete_action
                FROM pg_constraint con
                WHERE con.conname = ?
                    AND con.contype = 'f'
                    AND con.conrelid = (SELECT oid FROM pg_class
                                        WHERE relname = ? AND relnamespace =
                                            (SELECT oid FROM pg_namespace WHERE nspname = ?))
                """, name, TARGET, PUBLIC_SCHEMA);
        assertNotNull(row, () -> "foreign key not found: " + PUBLIC_SCHEMA + "." + TARGET + "." + name);
        return row;
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
                    fail("a concurrent ensure_foreign_key call did not finish within 30 s", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void callEnsureForeignKey(String name) {
        callEnsureForeignKey(name, "parent_id");
    }

    private void callEnsureForeignKey(String name, String... columns) {
        Routines.ensureForeignKey(dsl.configuration(),
                PUBLIC_SCHEMA, TARGET, name, columns,
                PUBLIC_SCHEMA, REFERENCED, new String[]{"id"});
    }

    private void callEnsureForeignKey(Connection connection, String name, String... columns) {
        Routines.ensureForeignKey(DSL.using(connection, SQLDialect.POSTGRES).configuration(),
                PUBLIC_SCHEMA, TARGET, name, columns,
                PUBLIC_SCHEMA, REFERENCED, new String[]{"id"});
    }

}

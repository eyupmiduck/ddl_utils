package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code ddl_utils.ensure_foreign_key} procedure: it adds a foreign
 * key as NOT VALID, commits, then validates it in a second committed step, and
 * recovers when called again after a partial failure.
 */
class EnsureForeignKeyProcedureTest extends PostgresTestBase {

    private static final String TARGET = "ensure_foreign_key_target";
    private static final String REFERENCED = "ensure_foreign_key_referenced";

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

        assertTrue(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));
        assertSqlState("23503",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)"));
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
    }

    /**
     * A same-named foreign key with a different definition (here, a different
     * referencing column) is not mistaken for the requested one: the procedure
     * fails loudly instead of silently accepting the wrong constraint.
     */
    @Test
    void rejectsSameNamedForeignKeyWithDifferentDefinition() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id)");

        assertSqlState("42710", () -> callEnsureForeignKey("fk_parent"));
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

        assertEquals("c", dsl.fetchOne(
                "SELECT confdeltype::text FROM pg_constraint WHERE conname = 'fk_parent'"
                        + " AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)",
                PUBLIC_SCHEMA).get(0, String.class));
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

        assertEquals("{2,1}", dsl.fetchOne(
                "SELECT confkey::text FROM pg_constraint WHERE conname = 'fk_parent'"
                        + " AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)",
                PUBLIC_SCHEMA).get(0, String.class));

        dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, "fk_parent",
                new String[]{"parent_id", "id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id", "code"});

        assertTrue(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));
    }

    /**
     * Referencing and referenced column lists of different lengths are rejected
     * up front rather than silently zipped (which would drop the surplus element
     * and could accept an under-specified definition).
     */
    @Test
    void rejectsMismatchedColumnListLengths() {
        assertSqlState("22023", () -> dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"parent_id", "id"},
                PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));
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
     * A second call on an already valid constraint is a no-op.
     */
    @Test
    void isIdempotent() {
        callEnsureForeignKey("fk_parent");

        callEnsureForeignKey("fk_parent");

        assertTrue(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));
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
        assertFalse(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));

        callEnsureForeignKey("fk_parent");

        assertTrue(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));
    }

    /**
     * An orphan row makes validation fail with a foreign-key violation, leaving
     * the constraint NOT VALID; the same call succeeds after the data is fixed.
     */
    @Test
    void failsOnOrphanRowThenSucceedsAfterFix() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)");

        assertSqlState("23503", () -> callEnsureForeignKey("fk_parent"));
        assertFalse(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));

        dsl.execute("UPDATE " + PUBLIC_SCHEMA + "." + TARGET + " SET parent_id = NULL WHERE parent_id = 999");
        callEnsureForeignKey("fk_parent");

        assertTrue(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));
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

        assertSqlState("42703", () -> dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"missing"}, PUBLIC_SCHEMA, REFERENCED,
                new String[]{"id"}));
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

        assertSqlState("42703", () -> dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"parent_id"}, PUBLIC_SCHEMA, REFERENCED,
                new String[]{"missing"}));
    }

    /**
     * A referenced table that does not exist is rejected with undefined_table.
     */
    @Test
    void rejectsUnknownReferencedTable() {
        assertSqlState("42P01", () -> dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, "fk_parent", new String[]{"parent_id"}, PUBLIC_SCHEMA, "no_such_table",
                new String[]{"id"}));
    }

    private void callEnsureForeignKey(String name) {
        dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, name,
                new String[]{"parent_id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id"});
    }

}

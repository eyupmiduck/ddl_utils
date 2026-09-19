package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private void callEnsureForeignKey(String name) {
        dsl.execute("CALL ddl_utils.ensure_foreign_key(?, ?, ?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, TARGET, name,
                new String[]{"parent_id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id"});
    }

}

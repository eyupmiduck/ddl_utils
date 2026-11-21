package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the metadata-only constraint helpers in {@code ddl_utils_lib}:
 * {@code add_check_constraint} and {@code add_foreign_key} create constraints
 * as {@code NOT VALID}, {@code validate_constraint} enforces them,
 * {@code drop_constraint} removes them and {@code rename_constraint} renames
 * them.
 */
class ConstraintTest extends PostgresTestBase {

    private static final String TARGET = "constraint_target";
    private static final String REFERENCED = "constraint_referenced";

    @BeforeEach
    void createTargetTables() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(TARGET, "id int, parent_id int, value int");
    }

    @AfterEach
    void dropTargetTables() {
        dropTestTable(TARGET);
        dropTestTable(REFERENCED);
    }

    /**
     * add_check_constraint creates the constraint as NOT VALID, so existing
     * violating rows do not fail the call.
     */
    @Test
    void addCheckConstraintIsNotValid() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (-1)");

        addCheckConstraint("value_positive", "value > 0");

        assertNotValidated(PUBLIC_SCHEMA, TARGET, "value_positive");
    }

    /**
     * validate_constraint enforces a NOT VALID check constraint.
     */
    @Test
    void validateCheckConstraintEnforcesIt() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (1)");
        addCheckConstraint("value_positive", "value > 0");

        validateConstraint("value_positive");

        assertValidated(PUBLIC_SCHEMA, TARGET, "value_positive");
        assertSqlState("23514",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (-1)"));
    }

    /**
     * validate_constraint fails when a NOT VALID constraint is violated by
     * existing rows.
     */
    @Test
    void validateFailsOnViolatingRows() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (-1)");
        addCheckConstraint("value_positive", "value > 0");

        assertSqlState("23514", () -> validateConstraint("value_positive"));
    }

    /**
     * A check expression with a top-level comma is rejected.
     */
    @Test
    void rejectsTopLevelCommaInCheckExpression() {
        assertSqlState("22023", () -> addCheckConstraint("c", "value > 0, DROP COLUMN id"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "id"));
    }

    /**
     * A comma nested in a parenthesised list is a legitimate CHECK expression,
     * not a second ALTER TABLE action.
     */
    @Test
    void acceptsNestedCommaInCheckExpression() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (1)");

        addCheckConstraint("value_in", "value IN (1, 2)");
        validateConstraint("value_in");

        assertValidated(PUBLIC_SCHEMA, TARGET, "value_in");
    }

    /**
     * add_foreign_key creates the FK as NOT VALID, so existing orphan rows do
     * not fail the call.
     */
    @Test
    void addForeignKeyIsNotValid() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)");

        addForeignKey("fk_parent", new String[]{"parent_id"}, REFERENCED, new String[]{"id"});

        assertNotValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
    }

    /**
     * validate_constraint enforces a NOT VALID foreign key.
     */
    @Test
    void validateForeignKeyEnforcesIt() {
        addForeignKey("fk_parent", new String[]{"parent_id"}, REFERENCED, new String[]{"id"});
        validateConstraint("fk_parent");

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_parent");
        assertSqlState("23503",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)"));
    }

    /**
     * A multi-column foreign key is created with the requested source-to-
     * referenced mapping, so a valid composite value is accepted and an orphan
     * composite value is rejected.
     */
    @Test
    void validatesCompositeForeignKey() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + REFERENCED + " ADD COLUMN code int");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + REFERENCED
                + " ADD CONSTRAINT ref_code_id UNIQUE (code, id)");
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + REFERENCED + " (id, code) VALUES (1, 10)");
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id, value) VALUES (10, 1)");

        addForeignKey("fk_composite", new String[]{"parent_id", "value"}, REFERENCED,
                new String[]{"code", "id"});
        validateConstraint("fk_composite");

        assertValidated(PUBLIC_SCHEMA, TARGET, "fk_composite");
        assertSqlState("23503", () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET
                + " (parent_id, value) VALUES (99, 1)"));
    }

    /**
     * add_foreign_key rejects referencing and referenced column lists of
     * different lengths.
     */
    @Test
    void rejectsMismatchedForeignKeyColumns() {
        assertSqlState("22023",
                () -> addForeignKey("fk_parent", new String[]{"parent_id", "id"}, REFERENCED, new String[]{"id"}));
    }

    /**
     * add_foreign_key rejects blank column names with an invalid-parameter
     * error.
     */
    @Test
    void rejectsBlankForeignKeyColumns() {
        assertSqlState("22023",
                () -> addForeignKey("fk_parent", new String[]{"  "}, REFERENCED, new String[]{"id"}));
        assertSqlState("22023",
                () -> addForeignKey("fk_parent", new String[]{"parent_id"}, REFERENCED, new String[]{"\t"}));
    }

    /**
     * The FK arguments reject null/empty arrays through their domains and a
     * missing referenced table with undefined_table.
     */
    @Test
    void rejectsNullEmptyAndMissingForeignKeyInputs() {
        assertDomainViolation(() -> addForeignKey(
                "fk_parent", null, REFERENCED, new String[]{"id"}));
        assertDomainViolation(() -> addForeignKey(
                "fk_parent", new String[0], REFERENCED, new String[]{"id"}));
        assertDomainViolation(() -> addForeignKey(
                "fk_parent", new String[]{"parent_id"}, REFERENCED, null));
        assertDomainViolation(() -> addForeignKey(
                "fk_parent", new String[]{"parent_id"}, REFERENCED, new String[0]));
        assertSqlState("42P01", () -> addForeignKey(
                "fk_parent", new String[]{"parent_id"}, "no_such_table", new String[]{"id"}));
    }

    /**
     * drop_constraint removes the constraint and a wrong name fails.
     */
    @Test
    void dropsConstraint() {
        addCheckConstraint("value_positive", "value > 0");

        dropConstraint("value_positive");

        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "value_positive"));
        assertSqlState("42704", () -> dropConstraint("missing"));
    }

    /**
     * drop_constraint always emits RESTRICT, so dropping a primary key that a
     * foreign key still references fails with dependent_objects_still_exist
     * (2BP01). Dependency order is the caller's responsibility: drop the
     * foreign key first.
     */
    @Test
    void dropConstraintRejectsReferencedPrimaryKey() {
        addForeignKey("fk_parent", new String[]{"parent_id"}, REFERENCED, new String[]{"id"});

        assertSqlState("2BP01", () -> Routines.dropConstraint(dsl.configuration(), PUBLIC_SCHEMA,
                REFERENCED, REFERENCED + "_pkey", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertTrue(constraintExists(PUBLIC_SCHEMA, REFERENCED, REFERENCED + "_pkey"),
                "the rejected drop must leave the referenced primary key in place");

        dropConstraint("fk_parent");
        Routines.dropConstraint(dsl.configuration(), PUBLIC_SCHEMA, REFERENCED,
                REFERENCED + "_pkey", DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
        assertFalse(constraintExists(PUBLIC_SCHEMA, REFERENCED, REFERENCED + "_pkey"));
    }

    /**
     * rename_constraint renames the constraint.
     */
    @Test
    void renamesConstraint() {
        addCheckConstraint("old_name", "value > 0");

        renameConstraint("old_name", "new_name");

        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertTrue(constraintExists(PUBLIC_SCHEMA, TARGET, "new_name"));

        assertSqlState("42704", () -> renameConstraint("missing", "other"));
        addCheckConstraint("other_name", "value > 0");
        assertSqlState("42710", () -> renameConstraint("new_name", "other_name"));
    }

    /**
     * Constraint names longer than NAMEDATALEN - 1 bytes are rejected instead of
     * being silently truncated by PostgreSQL. Multibyte names are measured in
     * bytes, not characters.
     */
    @Test
    void rejectsOverLongConstraintNames() {
        String tooLong = "c".repeat(64);
        String multibyte = "\u00E9".repeat(32); // 64 bytes, 32 characters

        assertSqlState("22023", () -> addCheckConstraint(tooLong, "value > 0"));
        assertSqlState("22023",
                () -> addForeignKey(tooLong, new String[]{"parent_id"}, REFERENCED, new String[]{"id"}));

        addCheckConstraint("short_name", "value > 0");
        assertSqlState("22023", () -> renameConstraint("short_name", tooLong));
        assertSqlState("22023", () -> renameConstraint(tooLong, "other_name"));
        assertSqlState("22023", () -> renameConstraint("short_name", multibyte));
    }

    /**
     * A constraint name of exactly 63 bytes (NAMEDATALEN - 1) is accepted.
     */
    @Test
    void acceptsConstraintNameAtTheLimit() {
        String name = "c".repeat(63);

        addCheckConstraint(name, "value > 0");

        assertTrue(constraintExists(PUBLIC_SCHEMA, TARGET, name));
    }

    /**
     * validate_constraint reports a missing constraint and handles a constraint
     * name that requires %I quoting.
     */
    @Test
    void validateReportsMissingAndQuotedConstraintNames() {
        assertSqlState("42704", () -> validateConstraint("missing"));

        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (1)");
        addCheckConstraint("Mixed Constraint", "value > 0");
        validateConstraint("Mixed Constraint");
        assertValidated(PUBLIC_SCHEMA, TARGET, "Mixed Constraint");
    }

    /**
     * The explicit-settings helper respects its lock budget under contention and
     * leaves no constraint behind.
     */
    @Test
    void addCheckConstraintGivesUpWhileTableLocked() throws Exception {
        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                        "blocked", "value > 0", 100, 100, 300));

        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "blocked"));
    }

    /**
     * Rejects null arguments through their domains.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> addCheckConstraint(null, "value > 0"));
        assertDomainViolation(() -> addCheckConstraint("c", null));
        assertDomainViolation(() -> validateConstraint(null));
        assertDomainViolation(() -> dropConstraint(null));
        assertDomainViolation(() -> renameConstraint("a", null));
        assertDomainViolation(() -> renameConstraint(null, "b"));
        assertDomainViolation(() -> addForeignKey(null, new String[]{"parent_id"}, REFERENCED, new String[]{"id"}));
        assertDomainViolation(() -> addForeignKey("fk", null, REFERENCED, new String[]{"id"}));
        assertDomainViolation(() -> addForeignKey("fk", new String[]{"parent_id"}, null, new String[]{"id"}));
        assertDomainViolation(() -> addForeignKey("fk", new String[]{"parent_id"}, REFERENCED, null));
    }

    /**
     * The constraint-name guards bound the name at 63 bytes: an over-long name
     * is rejected with 22023, while a 63-byte name passes the guard and fails
     * later only because no such constraint exists (42704). The referenced
     * schema and table of a foreign key are guarded the same way; a 63-byte
     * missing referenced table passes the guard and fails later as
     * undefined_table (42P01).
     */
    @Test
    void boundsConstraintAndReferencedNameLength() {
        String atLimit = "c".repeat(63);
        String tooLong = "c".repeat(64);

        assertSqlState("22023", () -> dropConstraint(tooLong));
        assertSqlState("42704", () -> dropConstraint(atLimit));
        assertSqlState("22023", () -> validateConstraint(tooLong));
        assertSqlState("42704", () -> validateConstraint(atLimit));

        assertSqlState("22023", () -> addForeignKey(
                "fk", new String[]{"parent_id"}, tooLong, new String[]{"id"}));
        assertSqlState("42P01", () -> addForeignKey(
                "fk", new String[]{"parent_id"}, atLimit, new String[]{"id"}));

        assertSqlState("22023", () -> Routines.addForeignKey(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "fk", new String[]{"parent_id"}, "s".repeat(64), REFERENCED, new String[]{"id"},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertSqlState("3F000", () -> Routines.addForeignKey(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                "fk", new String[]{"parent_id"}, "s".repeat(63), REFERENCED, new String[]{"id"},
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));    }

    private void addCheckConstraint(String name, String expression) {
        Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name, expression,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void addForeignKey(String name, String[] columns, String refTable, String[] refColumns) {
        Routines.addForeignKey(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name, columns,
                PUBLIC_SCHEMA, refTable, refColumns,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void validateConstraint(String name) {
        Routines.validateConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void dropConstraint(String name) {
        Routines.dropConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void renameConstraint(String name, String newName) {
        Routines.renameConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name, newName,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

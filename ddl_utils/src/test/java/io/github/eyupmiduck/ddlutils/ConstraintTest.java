package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

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

        assertFalse(constraintValidated(PUBLIC_SCHEMA, TARGET, "value_positive"));
    }

    /**
     * validate_constraint enforces a NOT VALID check constraint.
     */
    @Test
    void validateCheckConstraintEnforcesIt() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (1)");
        addCheckConstraint("value_positive", "value > 0");

        validateConstraint("value_positive");

        assertTrue(constraintValidated(PUBLIC_SCHEMA, TARGET, "value_positive"));
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
     * add_foreign_key creates the FK as NOT VALID, so existing orphan rows do
     * not fail the call.
     */
    @Test
    void addForeignKeyIsNotValid() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)");

        addForeignKey("fk_parent", new String[]{"parent_id"}, REFERENCED, new String[]{"id"});

        assertFalse(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));
    }

    /**
     * validate_constraint enforces a NOT VALID foreign key.
     */
    @Test
    void validateForeignKeyEnforcesIt() {
        addForeignKey("fk_parent", new String[]{"parent_id"}, REFERENCED, new String[]{"id"});
        validateConstraint("fk_parent");

        assertTrue(constraintValidated(PUBLIC_SCHEMA, TARGET, "fk_parent"));
        assertSqlState("23503",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (parent_id) VALUES (999)"));
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
     * rename_constraint renames the constraint.
     */
    @Test
    void renamesConstraint() {
        addCheckConstraint("old_name", "value > 0");

        renameConstraint("old_name", "new_name");

        assertFalse(constraintExists(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertTrue(constraintExists(PUBLIC_SCHEMA, TARGET, "new_name"));
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

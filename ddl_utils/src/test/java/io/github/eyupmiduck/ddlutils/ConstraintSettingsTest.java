package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware constraint wrappers {@code ddl_utils.add_check_constraint},
 * {@code validate_constraint}, {@code drop_constraint} and
 * {@code rename_constraint}: each resolves the table's lock settings through
 * {@code get_lock_settings} and delegates to the {@code ddl_utils_lib} helper.
 */
class ConstraintSettingsTest extends PostgresTestBase {

    private static final String TARGET = "constraint_settings_target";
    private static final String REFERENCED = "constraint_settings_referenced";

    @BeforeEach
    void createTargetTable() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(TARGET, "id int, value int, parent_id int");
    }

    @AfterEach
    void cleanUp() {
        try {
            clearTableLockSettings(PUBLIC_SCHEMA, TARGET);
        } finally {
            dropTestTable(TARGET);
            dropTestTable(REFERENCED);
        }
    }

    /**
     * add_check_constraint and validate_constraint use the database defaults.
     */
    @Test
    void usesDatabaseDefaults() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (value) VALUES (1)");

        Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "positive", "value > 0");
        Routines.validateConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "positive");

        assertTrue(constraintValidated("positive"));
    }

    /**
     * The wrappers pass the table-level lock settings to the helpers: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "positive", "value > 0"));
    }

    /**
     * drop_constraint and rename_constraint use the database defaults.
     */
    @Test
    void dropAndRenameUseDatabaseDefaults() {
        Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name", "value > 0");
        Routines.renameConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name", "new_name");
        assertTrue(constraintExists("new_name"));

        Routines.dropConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "new_name");
    }

    /**
     * drop_constraint and rename_constraint pass the table-level lock settings
     * to the helpers; a held ACCESS SHARE lock makes each give up quickly.
     */
    @Test
    void dropAndRenameUseTableLockSettings() throws Exception {
        Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name", "value > 0");
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.dropConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name"));
        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.renameConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name", "new_name"));
    }

    /**
     * add_foreign_key passes the table-level lock settings to the helper. It
     * takes SHARE ROW EXCLUSIVE, which an ACCESS SHARE lock does not block, so
     * the competing session holds ACCESS EXCLUSIVE.
     */
    @Test
    void addForeignKeyUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, "ACCESS EXCLUSIVE", 2000,
                () -> Routines.addForeignKey(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "fk_parent",
                        new String[]{"parent_id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id"}));
    }

    /**
     * validate_constraint passes the table-level lock settings to the helper.
     * It takes only SHARE UPDATE EXCLUSIVE, which an ACCESS SHARE lock does not
     * block, so the competing session holds ACCESS EXCLUSIVE.
     */
    @Test
    void validateConstraintUsesTableLockSettings() throws Exception {
        Routines.addCheckConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "positive", "value > 0");
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, "ACCESS EXCLUSIVE", 2000,
                () -> Routines.validateConstraint(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "positive"));
    }

    private boolean constraintValidated(String name) {
        return dsl.fetchOne(
                """
                        SELECT convalidated FROM pg_constraint
                        WHERE conname = ? AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        """,
                name, PUBLIC_SCHEMA).get(0, Boolean.class);
    }

    private boolean constraintExists(String name) {
        return dsl.fetchOne(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = ? AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        )
                        """,
                name, PUBLIC_SCHEMA).get(0, Boolean.class);
    }
}

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

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, value int");
    }

    @AfterEach
    void cleanUp() {
        try {
            clearTableLockSettings(PUBLIC_SCHEMA, TARGET);
        } finally {
            dropTestTable(TARGET);
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

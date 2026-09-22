package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware wrappers {@code ddl_utils.drop_column},
 * {@code ddl_utils.drop_columns} and {@code ddl_utils.rename_column}: they
 * resolve the table's lock settings via {@code get_lock_settings} and delegate
 * to the {@code ddl_utils_lib} helpers.
 */
class DropRenameColumnSettingsTest extends PostgresTestBase {

    private static final String TARGET = "drop_rename_column_settings_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, keep int, old_name int");
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
     * drop_column removes the column using the database defaults.
     */
    @Test
    void dropColumnUsesDatabaseDefaults() {
        Routines.dropColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name");

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "keep"));
    }

    /**
     * drop_columns removes several columns using the database defaults.
     */
    @Test
    void dropColumnsUsesDatabaseDefaults() {
        Routines.dropColumns(dsl.configuration(), PUBLIC_SCHEMA, TARGET, new String[]{"old_name", "keep"});

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "keep"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "id"));
    }

    /**
     * rename_column renames the column using the database defaults.
     */
    @Test
    void renameColumnUsesDatabaseDefaults() {
        Routines.renameColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name", "new_name");

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "new_name"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly, whereas the default would have
     * retried for ~30 s.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.dropColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name"));
    }

    /**
     * drop_columns resolves the table settings and gives up while the table is
     * locked.
     */
    @Test
    void dropColumnsUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.dropColumns(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                        new String[]{"old_name", "keep"}));
    }

    /**
     * rename_column resolves the table settings and gives up while the table is
     * locked.
     */
    @Test
    void renameColumnUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.renameColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name", "new_name"));
    }
}

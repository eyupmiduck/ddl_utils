package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware wrappers {@code ddl_utils.drop_column},
 * {@code ddl_utils.drop_columns} and {@code ddl_utils.rename_column}: they
 * resolve the table's lock settings via {@code get_lock_settings} and delegate
 * to the {@code ddl_utils_lib} helpers.
 */
class DropRenameColumnSettingsTest extends SingleTableTest {

    /**
     * The maximum time each call may take to give up. It must exceed the shared
     * statement budget ({@link PostgresTestBase#STATEMENT_DURATION}) but stay far
     * below the ~30 s database default, so a wrapper that ignored the table
     * settings fails the assertion instead of retrying for the full default.
     */
    private static final long GIVE_UP_MILLIS = 8_000;

    DropRenameColumnSettingsTest() {
        super("drop_rename_column_settings_target", "id int, keep int, old_name int");
    }

    /**
     * drop_column removes the column using the database defaults.
     */
    @Test
    void dropColumnUsesDatabaseDefaults() {
        Routines.dropColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), "old_name");

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
    }

    /**
     * drop_columns removes several columns using the database defaults.
     */
    @Test
    void dropColumnsUsesDatabaseDefaults() {
        Routines.dropColumns(dsl.configuration(), PUBLIC_SCHEMA, target(), new String[]{"old_name", "keep"});

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "id"));
    }

    /**
     * rename_column renames the column using the database defaults.
     */
    @Test
    void renameColumnUsesDatabaseDefaults() {
        Routines.renameColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), "old_name", "new_name");

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "new_name"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly, whereas the default would have
     * retried for ~30 s.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION, GIVE_UP_MILLIS,
                () -> Routines.dropColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), "old_name"));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"),
                "a call that gave up must not have dropped the column");
    }

    /**
     * drop_columns resolves the table settings and gives up while the table is
     * locked.
     */
    @Test
    void dropColumnsUsesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION, GIVE_UP_MILLIS,
                () -> Routines.dropColumns(dsl.configuration(), PUBLIC_SCHEMA, target(),
                        new String[]{"old_name", "keep"}));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"),
                "a call that gave up must not have dropped the columns");
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "keep"));
    }

    /**
     * rename_column resolves the table settings and gives up while the table is
     * locked.
     */
    @Test
    void renameColumnUsesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION, GIVE_UP_MILLIS,
                () -> Routines.renameColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), "old_name", "new_name"));

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "old_name"),
                "a call that gave up must not have renamed the column");
        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "new_name"));
    }
}

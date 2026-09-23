package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the lock-aware {@code ddl_utils.drop_column_default} wrapper: it
 * resolves the table's lock settings through {@code get_lock_settings} and
 * delegates to the {@code ddl_utils_lib} helper.
 */
class DropColumnDefaultSettingsTest extends SingleTableTest {

    DropColumnDefaultSettingsTest() {
        super("drop_column_default_settings_target", "id int, note text DEFAULT 'none'", true);
    }

    /**
     * Drops the default using the database defaults.
     */
    @Test
    void dropColumnDefaultUsesDatabaseDefaults() {
        Routines.dropColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, target(), "note");

        assertNull(columnAttribute(PUBLIC_SCHEMA, target(), "note", "column_default"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly, whereas the default would have
     * retried for ~30 s.
     */
    @Test
    void dropColumnDefaultUsesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                () -> Routines.dropColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, target(), "note"));
    }
}

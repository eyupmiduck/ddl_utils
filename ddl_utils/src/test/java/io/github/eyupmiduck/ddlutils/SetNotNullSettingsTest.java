package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware {@code ddl_utils.set_not_null} wrapper: it resolves
 * the table's lock settings through {@code get_lock_settings} and delegates to
 * the {@code ddl_utils_lib} helper.
 */
class SetNotNullSettingsTest extends SingleTableTest {

    SetNotNullSettingsTest() {
        super("set_not_null_settings_target", "id int, note text", true);
    }

    /**
     * Sets NOT NULL using the database defaults.
     */
    @Test
    void setNotNullUsesDatabaseDefaults() {
        Routines.setNotNull(dsl.configuration(), PUBLIC_SCHEMA, target(), "note");

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held ACCESS SHARE lock makes the call give up quickly.
     */
    @Test
    void setNotNullUsesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                () -> Routines.setNotNull(dsl.configuration(), PUBLIC_SCHEMA, target(), "note"));
    }
}

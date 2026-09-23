package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware {@code ddl_utils.set_column_default} wrapper: it
 * resolves the table's lock settings through {@code get_lock_settings} and
 * delegates to the {@code ddl_utils_lib} helper.
 */
class SetColumnDefaultSettingsTest extends SingleTableTest {

    SetColumnDefaultSettingsTest() {
        super("set_column_default_settings_target", "id int, note text", true);
    }

    /**
     * Sets the default using the database defaults.
     */
    @Test
    void setColumnDefaultUsesDatabaseDefaults() {
        Routines.setColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "'none'");

        String defaultExpression = columnAttribute(PUBLIC_SCHEMA, target(), "note", "column_default");
        assertNotNull(defaultExpression);
        assertTrue(defaultExpression.contains("'none'"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly, whereas the default would have
     * retried for ~30 s.
     */
    @Test
    void setColumnDefaultUsesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                () -> Routines.setColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "'none'"));
    }
}

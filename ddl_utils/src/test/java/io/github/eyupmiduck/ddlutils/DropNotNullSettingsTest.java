package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware {@code ddl_utils.drop_not_null} wrapper: it resolves
 * the table's lock settings through {@code get_lock_settings} and delegates to
 * the {@code ddl_utils_lib} helper.
 */
class DropNotNullSettingsTest extends SingleTableTest {

    DropNotNullSettingsTest() {
        super("drop_not_null_settings_target", "id int NOT NULL, note text NOT NULL", true);
    }

    /**
     * Drops NOT NULL using the database defaults.
     */
    @Test
    void dropNotNullUsesDatabaseDefaults() {
        Routines.dropNotNull(dsl.configuration(), PUBLIC_SCHEMA, target(), "note");

        assertNullable(PUBLIC_SCHEMA, target(), "note");
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly, whereas the default would have
     * retried for ~30 s.
     */
    @Test
    void dropNotNullUsesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                () -> Routines.dropNotNull(dsl.configuration(), PUBLIC_SCHEMA, target(), "note"));
    }
}

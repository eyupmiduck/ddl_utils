package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware column-attribute wrappers
 * {@code ddl_utils.set_column_storage} and {@code set_column_compression}: each
 * resolves the table's lock settings through {@code get_lock_settings} and
 * delegates to the {@code ddl_utils_lib} helper.
 */
class SetColumnAttributeSettingsTest extends SingleTableTest {

    SetColumnAttributeSettingsTest() {
        super("set_column_attribute_settings_target", "id int, note text", true);
    }

    /**
     * set_column_storage uses the database defaults.
     */
    @Test
    void setColumnStorageUsesDatabaseDefaults() {
        Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "EXTERNAL");

        assertEquals("external", columnStorage(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * set_column_compression uses the database defaults.
     */
    @Test
    void setColumnCompressionUsesDatabaseDefaults() {
        Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "pglz");

        assertEquals("pglz", columnCompression(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * set_column_storage and set_column_compression pass the table-level lock
     * settings to the helpers: the table's statement budget is far below the
     * database default (30000 ms), so a held ACCESS SHARE lock makes each call
     * give up quickly.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, target(), 100, 100, 300);

        assertGivesUpWhileTableLocked(target(), 2000,
                () -> Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "EXTERNAL"));
        assertGivesUpWhileTableLocked(target(), 2000,
                () -> Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "pglz"));
    }

}

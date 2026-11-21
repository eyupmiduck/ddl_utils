package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the lock-aware column-attribute wrappers
 * {@code ddl_utils.set_column_storage} and {@code set_column_compression}: each
 * resolves the table's lock settings through {@code get_lock_settings} and
 * delegates to the {@code ddl_utils_lib} helper.
 */
class SetColumnAttributeSettingsTest extends SingleTableTest {

    SetColumnAttributeSettingsTest() {
        super("set_column_attribute_settings_target", "id int, note text");
    }

    /**
     * set_column_storage uses the database defaults.
     */
    @Test
    void setColumnStorageUsesDatabaseDefaults() {
        assertNull(getTableLockSettings(PUBLIC_SCHEMA, target()),
                "the test must start with no table-level override");

        Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "EXTERNAL");

        assertEquals("external", columnStorage(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * set_column_compression uses the database defaults. The chosen method is
     * lz4 rather than the server default pglz, so a no-op wrapper cannot pass.
     */
    @Test
    void setColumnCompressionUsesDatabaseDefaults() {
        assertNull(getTableLockSettings(PUBLIC_SCHEMA, target()),
                "the test must start with no table-level override");

        Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "lz4");

        assertEquals("lz4", columnCompression(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * set_column_storage and set_column_compression pass the table-level lock
     * settings to the helpers: the table's statement budget is far below the
     * database default (30000 ms), so a held ACCESS SHARE lock makes each call
     * give up quickly.
     */
    @Test
    void setColumnStorageUsesTableLockSettings() throws Exception {
        String before = columnStorage(PUBLIC_SCHEMA, target(), "note");

        assertUsesTableLockSettings(
                () -> Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "EXTERNAL"));

        assertEquals(before, columnStorage(PUBLIC_SCHEMA, target(), "note"),
                "a call that gave up must not have changed the storage");
    }

    /**
     * set_column_compression resolves the table settings and gives up while the
     * table is locked, leaving the compression unchanged. The attempted method
     * is lz4 while the column starts at the server default pglz, so a call that
     * wrongly succeeded would be visible.
     */
    @Test
    void setColumnCompressionUsesTableLockSettings() throws Exception {
        String before = columnCompression(PUBLIC_SCHEMA, target(), "note");

        assertUsesTableLockSettings(
                () -> Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, target(), "note", "lz4"));

        assertEquals(before, columnCompression(PUBLIC_SCHEMA, target(), "note"),
                "a call that gave up must not have changed the compression");
    }

}

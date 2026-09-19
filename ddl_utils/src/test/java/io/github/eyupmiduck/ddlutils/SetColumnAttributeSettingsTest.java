package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware column-attribute wrappers
 * {@code ddl_utils.set_column_storage} and {@code set_column_compression}: each
 * resolves the table's lock settings through {@code get_lock_settings} and
 * delegates to the {@code ddl_utils_lib} helper.
 */
class SetColumnAttributeSettingsTest extends PostgresTestBase {

    private static final String TARGET = "set_column_attribute_settings_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, note text");
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
     * set_column_storage uses the database defaults.
     */
    @Test
    void setColumnStorageUsesDatabaseDefaults() {
        Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", "EXTERNAL");

        assertEquals("external", columnStorage(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * set_column_compression uses the database defaults.
     */
    @Test
    void setColumnCompressionUsesDatabaseDefaults() {
        Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", "pglz");

        assertEquals("pglz", columnCompression(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * set_column_storage and set_column_compression pass the table-level lock
     * settings to the helpers: the table's statement budget is far below the
     * database default (30000 ms), so a held ACCESS SHARE lock makes each call
     * give up quickly.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", "EXTERNAL"));
        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", "pglz"));
    }

}

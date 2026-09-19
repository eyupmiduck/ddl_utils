package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the lock-aware {@code ddl_utils.drop_column_default} wrapper: it
 * resolves the table's lock settings through {@code get_lock_settings} and
 * delegates to the {@code ddl_utils_lib} helper.
 */
class DropColumnDefaultSettingsTest extends PostgresTestBase {

    private static final String TARGET = "drop_column_default_settings_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, note text DEFAULT 'none'");
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
     * Drops the default using the database defaults.
     */
    @Test
    void dropColumnDefaultUsesDatabaseDefaults() {
        Routines.dropColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note");

        assertNull(columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "column_default"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly, whereas the default would have
     * retried for ~30 s.
     */
    @Test
    void dropColumnDefaultUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.dropColumnDefault(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note"));
    }
}

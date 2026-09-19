package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware {@code ddl_utils.set_not_null} wrapper: it resolves
 * the table's lock settings through {@code get_lock_settings} and delegates to
 * the {@code ddl_utils_lib} helper.
 */
class SetNotNullSettingsTest extends PostgresTestBase {

    private static final String TARGET = "set_not_null_settings_target";

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
     * Sets NOT NULL using the database defaults.
     */
    @Test
    void setNotNullUsesDatabaseDefaults() {
        Routines.setNotNull(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note");

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held ACCESS SHARE lock makes the call give up quickly.
     */
    @Test
    void setNotNullUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.setNotNull(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note"));
    }
}

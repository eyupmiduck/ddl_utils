package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware {@code ddl_utils.add_identity} and
 * {@code ddl_utils.drop_identity} wrappers: they resolve the table's lock
 * settings through {@code get_lock_settings} and delegate to the
 * {@code ddl_utils_lib} helpers.
 */
class AddDropIdentitySettingsTest extends PostgresTestBase {

    private static final String TARGET = "add_drop_identity_settings_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int NOT NULL, note text");
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
     * add_identity and drop_identity use the database defaults.
     */
    @Test
    void usesDatabaseDefaults() {
        Routines.addIdentity(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "id", "ALWAYS");
        Routines.dropIdentity(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "id", true);

        assertEquals("", dsl.fetchOne(
                """
                        SELECT a.attidentity::text
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, "id").get(0, String.class));
    }

    /**
     * The wrappers pass the table-level lock settings to the helpers: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.addIdentity(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "id", "ALWAYS"));
    }
}

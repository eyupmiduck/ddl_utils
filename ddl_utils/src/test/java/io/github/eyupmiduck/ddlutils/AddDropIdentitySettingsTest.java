package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware {@code ddl_utils.add_identity} and
 * {@code ddl_utils.drop_identity} wrappers: they resolve the table's lock
 * settings through {@code get_lock_settings} and delegate to the
 * {@code ddl_utils_lib} helpers.
 */
class AddDropIdentitySettingsTest extends SingleTableTest {

    private static final String IDENTITY_ALWAYS = "ALWAYS";
    private static final boolean DROP_IDENTITY_IF_EXISTS = true;

    AddDropIdentitySettingsTest() {
        super("add_drop_identity_settings_target", "id int NOT NULL, note text", true);
    }

    /**
     * add_identity and drop_identity use the database defaults: with a
     * distinctive short database budget and no table/schema override, both
     * wrappers give up quickly while the table is locked (and only succeed once
     * it is free), proving they read the database row rather than a hard-coded
     * value.
     */
    @Test
    void usesDatabaseDefaults() throws Exception {
        setDatabaseLockSettings(50, 10, 300);
        try {
            assertGivesUpWhileTableLocked(target(), 2000,
                    () -> Routines.addIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), "id",
                            IDENTITY_ALWAYS));
            assertEquals("", columnIdentity(PUBLIC_SCHEMA, target(), "id"),
                    "a call that gave up must not have added the identity");

            Routines.addIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), "id", IDENTITY_ALWAYS);
            assertEquals("a", columnIdentity(PUBLIC_SCHEMA, target(), "id"));

            assertGivesUpWhileTableLocked(target(), 2000,
                    () -> Routines.dropIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), "id",
                            DROP_IDENTITY_IF_EXISTS));

            Routines.dropIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), "id", DROP_IDENTITY_IF_EXISTS);
            assertEquals("", columnIdentity(PUBLIC_SCHEMA, target(), "id"));
        } finally {
            setDatabaseLockSettings(100, 1000, 30000);
        }
    }

    /**
     * The wrappers pass the table-level lock settings to the helpers: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held ACCESS SHARE lock makes each call give up quickly.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                () -> Routines.addIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), "id", IDENTITY_ALWAYS),
                () -> Routines.dropIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), "id", DROP_IDENTITY_IF_EXISTS));
    }
}

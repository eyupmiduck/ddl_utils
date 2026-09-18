package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware wrappers {@code ddl_utils.drop_column} and
 * {@code ddl_utils.rename_column}: they resolve the table's lock settings via
 * {@code get_lock_settings} and delegate to the {@code ddl_utils_lib} helpers.
 */
class DropRenameColumnSettingsTest extends PostgresTestBase {

    private static final String TARGET = "drop_rename_column_settings_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, keep int, old_name int");
    }

    @AfterEach
    void cleanUp() {
        try {
            Routines.clearTableLockSettings(dsl.configuration(), PUBLIC_SCHEMA, TARGET);
        } finally {
            dropTestTable(TARGET);
        }
    }

    /**
     * drop_column removes the column using the database defaults.
     */
    @Test
    void dropColumnUsesDatabaseDefaults() {
        Routines.dropColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name");

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "keep"));
    }

    /**
     * drop_columns removes several columns using the database defaults.
     */
    @Test
    void dropColumnsUsesDatabaseDefaults() {
        Routines.dropColumns(dsl.configuration(), PUBLIC_SCHEMA, TARGET, new String[]{"old_name", "keep"});

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "keep"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "id"));
    }

    /**
     * rename_column renames the column using the database defaults.
     */
    @Test
    void renameColumnUsesDatabaseDefaults() {
        Routines.renameColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name", "new_name");

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "old_name"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "new_name"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: with a
     * competing session holding a lock and a long table-level statement
     * duration, the call retries and succeeds once the lock is released,
     * whereas the short database default would have given up first.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        Routines.setTableLockSettings(dsl.configuration(), PUBLIC_SCHEMA, TARGET, 100, 100, 30000);

        try (Connection other = openTestConnection()) {
            holdAccessShareLock(other, TARGET);
            awaitAccessShareLockHeld(TARGET);

            CompletableFuture<Void> release = rollbackAfter(other, 1000);
            try {
                Routines.dropColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "old_name");
            } finally {
                release.join();
            }
        }

        assertFalse(hasColumn(PUBLIC_SCHEMA, TARGET, "old_name"));
    }
}

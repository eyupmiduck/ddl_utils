package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware {@code ddl_utils.add_column} and
 * {@code ddl_utils.add_columns} wrappers: they resolve lock settings through
 * {@code ddl_utils.get_lock_settings} and delegate the actual DDL to the
 * {@code ddl_utils_lib} helpers.
 */
class AddColumnSettingsTest extends PostgresTestBase {

    private static final String TARGET = "add_column_settings_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int");
    }

    @AfterEach
    void cleanUp() {
        clearTableLockSettings(PUBLIC_SCHEMA, TARGET);
        dropTestTable(TARGET);
    }

    /**
     * add_column adds the column using the database defaults when no table or
     * schema override exists.
     */
    @Test
    void addColumnUsesDatabaseDefaults() {
        addColumn("note", "text", "'none'", false);

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "note"));
        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertTrue(columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "column_default").contains("'none'"));
    }

    /**
     * add_columns adds several columns in one call using the database defaults.
     */
    @Test
    void addColumnsUsesDatabaseDefaults() {
        addColumns(
                new String[]{"first", "second"},
                new String[]{"int", "text"},
                new String[]{null, null},
                new Boolean[]{true, false});

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "first"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "second"));
        assertNull(columnAttribute(PUBLIC_SCHEMA, TARGET, "first", "column_default"));
        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "second", "is_nullable"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: with a
     * competing session holding a lock and a long table-level statement
     * duration, the call keeps retrying and succeeds once the lock is released,
     * whereas the short database default would have given up first.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 30000);

        try (Connection other = openTestConnection()) {
            holdAccessShareLock(other, TARGET);
            awaitAccessShareLockHeld(TARGET);

            CompletableFuture<Void> release = rollbackAfter(other, 1000);

            addColumn("eventually", "int", null, true);
            release.join();
        }

        assertTrue(hasColumn(PUBLIC_SCHEMA, TARGET, "eventually"));
    }

    /**
     * add_column rejects null text and nullable arguments through the domains
     * before the body runs.
     */
    @Test
    void addColumnRejectsNullArguments() {
        assertDomainViolation(() -> addColumn(null, "text", null, true));
        assertDomainViolation(() -> addColumn("note", null, null, true));
        assertDomainViolation(() -> addColumn("note", "text", null, null));
    }

    /**
     * add_columns rejects empty arrays through its array domains before the
     * body runs.
     */
    @Test
    void addColumnsRejectsEmptyArraysThroughDomains() {
        assertDomainViolation(() -> addColumns(
                new String[0], new String[0], new String[0], new Boolean[0]));
    }

    private void addColumn(String column, String type, String defaultValue, Boolean nullable) {
        Routines.addColumn(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column, type, defaultValue, nullable);
    }

    private void addColumns(String[] names, String[] types, String[] defaults, Boolean[] nullable) {
        Routines.addColumns(dsl.configuration(), PUBLIC_SCHEMA, TARGET, names, types, defaults, nullable);
    }

    private void setTableLockSettings(String schema, String table, Integer lockTimeout,
                                      Integer sleepTime, Integer duration) {
        Routines.setTableLockSettings(dsl.configuration(), schema, table, lockTimeout, sleepTime, duration);
    }

    private void clearTableLockSettings(String schema, String table) {
        Routines.clearTableLockSettings(dsl.configuration(), schema, table);
    }
}

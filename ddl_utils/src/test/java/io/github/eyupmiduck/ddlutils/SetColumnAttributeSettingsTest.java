package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the lock-aware column-attribute wrappers
 * {@code ddl_utils.set_column_statistics}, {@code set_column_storage},
 * {@code set_column_compression} and {@code drop_expression}: each resolves the
 * table's lock settings through {@code get_lock_settings} and delegates to the
 * {@code ddl_utils_lib} helper.
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
     * set_column_statistics uses the database defaults.
     */
    @Test
    void setColumnStatisticsUsesDatabaseDefaults() {
        Routines.setColumnStatistics(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", 500);

        assertEquals(500, statistics("note"));
    }

    /**
     * set_column_storage uses the database defaults.
     */
    @Test
    void setColumnStorageUsesDatabaseDefaults() {
        Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", "EXTERNAL");

        assertEquals("external", storage("note"));
    }

    /**
     * set_column_compression uses the database defaults.
     */
    @Test
    void setColumnCompressionUsesDatabaseDefaults() {
        Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", "pglz");

        assertEquals("pglz", compression("note"));
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

    /**
     * set_column_statistics also passes the table-level settings to the helper.
     * It takes only SHARE UPDATE EXCLUSIVE, which an ACCESS SHARE lock does not
     * block, so the competing session holds ACCESS EXCLUSIVE instead.
     */
    @Test
    void setColumnStatisticsUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, "ACCESS EXCLUSIVE", 2000,
                () -> Routines.setColumnStatistics(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "note", 500));
    }

    private Integer statistics(String column) {
        return dsl.fetchOne(
                """
                        SELECT a.attstattarget
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, Integer.class);
    }

    private String storage(String column) {
        String code = dsl.fetchOne(
                """
                        SELECT a.attstorage::text
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, String.class);
        return switch (code) {
            case "p" -> "plain";
            case "e" -> "external";
            case "m" -> "main";
            case "x" -> "extended";
            default -> code;
        };
    }

    private String compression(String column) {
        String code = dsl.fetchOne(
                """
                        SELECT a.attcompression::text
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, String.class);
        return switch (code) {
            case "p" -> "pglz";
            case "l" -> "lz4";
            case "" -> "default";
            default -> code;
        };
    }
}

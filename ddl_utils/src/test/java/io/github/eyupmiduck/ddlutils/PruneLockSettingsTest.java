package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.jooq.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@code ddl_utils.prune_orphaned_lock_settings}: dry run is
 * the default, a real run deletes only the candidates, live tables are kept,
 * and missing-schema rows require the explicit flag.
 */
class PruneLockSettingsTest extends PostgresTestBase {

    private static final String MISSING_TABLE = "prune_missing_table";
    private static final String MISSING_SCHEMA = "prune_missing_schema";
    private static final String REAL_TABLE = "prune_real_table";
    private static final String VIEW_NAME = "prune_view_relation";

    @AfterEach
    void cleanUp() {
        clearTableLockSettings(PUBLIC_SCHEMA, MISSING_TABLE);
        clearTableLockSettings(MISSING_SCHEMA, "t");
        clearSchemaLockSettings(MISSING_SCHEMA);
        clearTableLockSettings(PUBLIC_SCHEMA, REAL_TABLE);
        clearTableLockSettings(PUBLIC_SCHEMA, VIEW_NAME);
        dsl.execute("DROP VIEW IF EXISTS " + PUBLIC_SCHEMA + "." + VIEW_NAME);
        dsl.execute("DROP TABLE IF EXISTS " + PUBLIC_SCHEMA + "." + REAL_TABLE);
    }

    /**
     * The default call is a dry run: it reports the existing-schema orphan and
     * deletes nothing, leaving missing-schema rows alone.
     */
    @Test
    void dryRunIsTheDefaultAndDeletesNothing() {
        setTableLockSettings(PUBLIC_SCHEMA, MISSING_TABLE, 1, 2, 3);
        setTableLockSettings(MISSING_SCHEMA, "t", 4, 5, 6);
        setSchemaLockSettings(MISSING_SCHEMA, 7, 8, 9);

        Result<Record> rows = dsl.fetch("SELECT * FROM ddl_utils.prune_orphaned_lock_settings()");
        assertNotNull(tableRow(rows, PUBLIC_SCHEMA, MISSING_TABLE));
        assertEquals("would_delete", tableRow(rows, PUBLIC_SCHEMA, MISSING_TABLE)
                .get("action", String.class));
        assertFalse(hasSchemaRow(rows, MISSING_SCHEMA));
        assertFalse(hasTableRow(rows, MISSING_SCHEMA, "t"));

        assertTrue(tableSettingsExist(PUBLIC_SCHEMA, MISSING_TABLE));
        assertTrue(tableSettingsExist(MISSING_SCHEMA, "t"));
        assertTrue(schemaSettingsExist(MISSING_SCHEMA));
    }

    /**
     * A real run with the default missing-schema flag deletes the orphan in the
     * existing schema but keeps the missing-schema settings.
     */
    @Test
    void realRunKeepsMissingSchemaRowsByDefault() {
        setTableLockSettings(PUBLIC_SCHEMA, MISSING_TABLE, 1, 2, 3);
        setTableLockSettings(MISSING_SCHEMA, "t", 4, 5, 6);
        setSchemaLockSettings(MISSING_SCHEMA, 7, 8, 9);

        Result<Record> rows = dsl.fetch(
                "SELECT * FROM ddl_utils.prune_orphaned_lock_settings(false, false)");
        assertNotNull(tableRow(rows, PUBLIC_SCHEMA, MISSING_TABLE));
        assertEquals("deleted", tableRow(rows, PUBLIC_SCHEMA, MISSING_TABLE)
                .get("action", String.class));

        assertFalse(tableSettingsExist(PUBLIC_SCHEMA, MISSING_TABLE));
        assertTrue(tableSettingsExist(MISSING_SCHEMA, "t"));
        assertTrue(schemaSettingsExist(MISSING_SCHEMA));
    }

    /**
     * The explicit missing-schema flag deletes the remaining schema/table rows.
     */
    @Test
    void includeMissingSchemasDeletesThem() {
        setTableLockSettings(MISSING_SCHEMA, "t", 4, 5, 6);
        setSchemaLockSettings(MISSING_SCHEMA, 7, 8, 9);

        Result<Record> rows = dsl.fetch(
                "SELECT * FROM ddl_utils.prune_orphaned_lock_settings(false, true)");
        assertFalse(tableSettingsExist(MISSING_SCHEMA, "t"));
        assertFalse(schemaSettingsExist(MISSING_SCHEMA));
        assertNotNull(tableRow(rows, MISSING_SCHEMA, "t"));
        assertTrue(hasSchemaRow(rows, MISSING_SCHEMA));
    }

    /**
     * A live table is never a candidate, while a view with table settings is
     * reported and deleted because it is not an ordinary/partitioned table.
     */
    @Test
    void keepsLiveTableAndPrunesNonTableRelation() {
        createTestTable(REAL_TABLE, "id int");
        setTableLockSettings(PUBLIC_SCHEMA, REAL_TABLE, 1, 2, 3);
        dsl.execute("CREATE VIEW " + PUBLIC_SCHEMA + "." + VIEW_NAME + " AS SELECT 1 AS id");
        setTableLockSettings(PUBLIC_SCHEMA, VIEW_NAME, 4, 5, 6);

        Result<Record> rows = dsl.fetch(
                "SELECT * FROM ddl_utils.prune_orphaned_lock_settings(false, false)");

        assertTrue(tableSettingsExist(PUBLIC_SCHEMA, REAL_TABLE));
        assertFalse(tableSettingsExist(PUBLIC_SCHEMA, VIEW_NAME));
        assertTrue(tableExists(PUBLIC_SCHEMA, REAL_TABLE));
        assertNotNull(tableRow(rows, PUBLIC_SCHEMA, VIEW_NAME));
        assertFalse(hasTableRow(rows, PUBLIC_SCHEMA, REAL_TABLE));
    }

    private Record tableRow(Result<Record> rows, String schema, String table) {
        return rows.stream()
                .filter(row -> "table".equals(row.get("level", String.class)))
                .filter(row -> schema.equals(row.get("schema_name", String.class)))
                .filter(row -> table.equals(row.get("table_name", String.class)))
                .findFirst()
                .orElse(null);
    }

    private boolean hasTableRow(Result<Record> rows, String schema, String table) {
        return tableRow(rows, schema, table) != null;
    }

    private boolean hasSchemaRow(Result<Record> rows, String schema) {
        return rows.stream()
                .anyMatch(row -> "schema".equals(row.get("level", String.class))
                        && schema.equals(row.get("schema_name", String.class)));
    }

    private boolean tableSettingsExist(String schema, String table) {
        return dsl.fetchOne(
                "SELECT 1 FROM ddl_utils.table_lock_settings WHERE schema_name = ? AND table_name = ?",
                schema, table) != null;
    }

    private boolean schemaSettingsExist(String schema) {
        return dsl.fetchOne(
                "SELECT 1 FROM ddl_utils.schema_lock_settings WHERE schema_name = ?", schema) != null;
    }
}

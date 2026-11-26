package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Direct tests for the {@code ddl_utils.lock_settings_orphans} view: missing
 * tables and schemas are reported with existence flags, live tables are
 * excluded, and non-table relations are reported with their relation kind.
 */
class LockSettingsOrphansViewTest extends PostgresTestBase {

    private static final String MISSING_TABLE = "orphan_view_missing";
    private static final String MISSING_SCHEMA = "orphan_view_missing_schema";
    private static final String LIVE_TABLE = "orphan_view_live";
    private static final String VIEW_NAME = "orphan_view_relation";

    @AfterEach
    void cleanUp() {
        clearTableLockSettings(PUBLIC_SCHEMA, MISSING_TABLE);
        clearTableLockSettings(MISSING_SCHEMA, "t");
        clearSchemaLockSettings(MISSING_SCHEMA);
        clearTableLockSettings(PUBLIC_SCHEMA, LIVE_TABLE);
        clearTableLockSettings(PUBLIC_SCHEMA, VIEW_NAME);
        dsl.execute("DROP VIEW IF EXISTS " + PUBLIC_SCHEMA + "." + VIEW_NAME);
        dropTestTable(LIVE_TABLE);
    }

    /**
     * A table settings row for a missing table in an existing schema is
     * reported with schema_exists true and relation_exists false.
     */
    @Test
    void reportsMissingTableInExistingSchema() {
        setTableLockSettings(PUBLIC_SCHEMA, MISSING_TABLE, 111, 222, 333);

        Record row = tableRow(MISSING_TABLE);
        assertNotNull(row);
        assertEquals(true, row.get("schema_exists", Boolean.class));
        assertEquals(false, row.get("relation_exists", Boolean.class));
        assertNull(row.get("relation_kind", String.class));
        assertEquals(111, row.get("ddl_lock_timeout", Integer.class));
        assertEquals(222, row.get("sleep_time", Integer.class));
        assertEquals(333, row.get("statement_duration", Integer.class));
    }

    /**
     * Missing-schema table and schema settings are both reported with
     * schema_exists false; the schema row has no table name or relation flag.
     */
    @Test
    void reportsMissingSchemaSettingsAndTables() {
        setSchemaLockSettings(MISSING_SCHEMA, 1, 2, 3);
        setTableLockSettings(MISSING_SCHEMA, "t", 4, 5, 6);

        Record schema = dsl.fetchOne("""
                SELECT *
                FROM ddl_utils.lock_settings_orphans
                WHERE level = 'schema' AND schema_name = ?
                """, MISSING_SCHEMA);
        assertNotNull(schema);
        assertNull(schema.get("table_name", String.class));
        assertEquals(false, schema.get("schema_exists", Boolean.class));
        assertNull(schema.get("relation_exists", Boolean.class));

        Record table = tableRow(MISSING_SCHEMA, "t");
        assertNotNull(table);
        assertEquals(false, table.get("schema_exists", Boolean.class));
        assertEquals(false, table.get("relation_exists", Boolean.class));
    }

    /**
     * A live table is excluded, while a view with settings is reported as a
     * non-table relation with relation_kind 'v'.
     */
    @Test
    void excludesLiveTableAndReportsNonTableRelation() {
        createTestTable(LIVE_TABLE, "id int");
        setTableLockSettings(PUBLIC_SCHEMA, LIVE_TABLE, 1, 2, 3);
        assertNull(tableRow(LIVE_TABLE));

        dsl.execute("CREATE VIEW " + PUBLIC_SCHEMA + "." + VIEW_NAME + " AS SELECT 1 AS id");
        setTableLockSettings(PUBLIC_SCHEMA, VIEW_NAME, 4, 5, 6);

        Record view = tableRow(VIEW_NAME);
        assertNotNull(view);
        assertEquals(true, view.get("schema_exists", Boolean.class));
        assertEquals(true, view.get("relation_exists", Boolean.class));
        assertEquals("v", view.get("relation_kind", String.class));
    }

    private Record tableRow(String table) {
        return tableRow(PUBLIC_SCHEMA, table);
    }

    private Record tableRow(String schema, String table) {
        return dsl.fetchOne("""
                SELECT *
                FROM ddl_utils.lock_settings_orphans
                WHERE level = 'table' AND schema_name = ? AND table_name = ?
                """, schema, table);
    }
}

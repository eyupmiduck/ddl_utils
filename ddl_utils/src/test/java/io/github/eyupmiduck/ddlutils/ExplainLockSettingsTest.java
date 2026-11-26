package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@code ddl_utils.explain_lock_settings}: it reports the
 * effective timings, the winning table/schema/database source, and the health
 * warnings for those timings.
 */
class ExplainLockSettingsTest extends PostgresTestBase {

    private static final String TABLE = "explain_lock_target";
    private static final String MISSING_TABLE = "explain_lock_missing";

    @AfterEach
    void cleanUp() {
        clearTableLockSettings(PUBLIC_SCHEMA, TABLE);
        clearTableLockSettings(PUBLIC_SCHEMA, MISSING_TABLE);
        clearSchemaLockSettings(PUBLIC_SCHEMA);
    }

    /**
     * A table override wins and supplies the table source.
     */
    @Test
    void tableOverrideWins() {
        setTableLockSettings(PUBLIC_SCHEMA, TABLE, 111, 222, 333);

        Record row = explain(TABLE);
        assertNotNull(row);
        assertEquals(111, row.get("ddl_lock_timeout", Integer.class));
        assertEquals(222, row.get("sleep_time", Integer.class));
        assertEquals(333, row.get("statement_duration", Integer.class));
        assertEquals("table", row.get("source", String.class));
        assertEquals("", row.get("warnings", String.class));
    }

    /**
     * Without a table row, a schema row is the source.
     */
    @Test
    void schemaOverrideWinsOverDatabase() {
        setSchemaLockSettings(PUBLIC_SCHEMA, 444, 555, 666);

        Record row = explain(MISSING_TABLE);
        assertNotNull(row);
        assertEquals(444, row.get("ddl_lock_timeout", Integer.class));
        assertEquals(555, row.get("sleep_time", Integer.class));
        assertEquals(666, row.get("statement_duration", Integer.class));
        assertEquals("schema", row.get("source", String.class));
    }

    /**
     * Without table or schema rows, the database defaults are the source.
     */
    @Test
    void databaseDefaultsAreTheFallback() {
        Record row = explain(MISSING_TABLE);
        assertNotNull(row);
        assertEquals(100, row.get("ddl_lock_timeout", Integer.class));
        assertEquals(1000, row.get("sleep_time", Integer.class));
        assertEquals(30000, row.get("statement_duration", Integer.class));
        assertEquals("database", row.get("source", String.class));
    }

    /**
     * A table override can be pre-provisioned for a missing table, and its
     * risky timings produce warnings without rejecting the settings.
     */
    @Test
    void reportsWarningsForPreProvisionedTable() {
        setTableLockSettings(PUBLIC_SCHEMA, MISSING_TABLE, 0, 0, 0);

        Record row = explain(MISSING_TABLE);
        assertNotNull(row);
        assertEquals("table", row.get("source", String.class));
        assertEquals(0, row.get("ddl_lock_timeout", Integer.class));
        assertTrue(row.get("warnings", String.class).contains("timeout is disabled"));
        assertTrue(row.get("warnings", String.class).contains("busy-wait"));
        assertTrue(row.get("warnings", String.class).contains("no retry"));
    }

    private Record explain(String table) {
        return dsl.fetchOne("""
                SELECT ddl_lock_timeout,
                       sleep_time,
                       statement_duration,
                       source,
                       pg_catalog.array_to_string(warnings, '|') AS warnings
                FROM ddl_utils.explain_lock_settings(?, ?)
                """, PUBLIC_SCHEMA, table);
    }
}

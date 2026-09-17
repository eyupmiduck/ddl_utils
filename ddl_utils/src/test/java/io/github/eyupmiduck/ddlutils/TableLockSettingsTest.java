package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.Routines;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@code ddl_utils.table_lock_settings} and its accessors: the caller
 * can read the table, {@code get_table_lock_settings} returns a table's row, and
 * {@code set_table_lock_settings}/{@code clear_table_lock_settings} upsert and
 * delete it (SECURITY DEFINER, since the caller has only SELECT).
 */
class TableLockSettingsTest extends PostgresTestBase {

    private static final String SCHEMA = "table_lock_settings_test_schema";
    private static final String TABLE = "table_lock_settings_test_table";
    private static final String CLEAR_TABLE = "table_lock_settings_clear_table";

    /**
     * get_table_lock_settings returns no row for a table that has no settings.
     */
    @Test
    void getTableLockSettingsReturnsNothingForUnknownTable() {
        assertNull(getTableLockSettings("absent_schema", "absent_table"));
    }

    /**
     * set_table_lock_settings inserts a row for a new table and updates it on a
     * second call, with get_table_lock_settings reflecting both.
     */
    @Test
    void setTableLockSettingsInsertsAndUpdates() {
        setTableLockSettings(SCHEMA, TABLE, 200, 300, 40);
        Record inserted = getTableLockSettings(SCHEMA, TABLE);
        assertNotNull(inserted);
        assertEquals(200, inserted.get("ddl_lock_timeout", Integer.class));
        assertEquals(300, inserted.get("sleep_time", Integer.class));
        assertEquals(40, inserted.get("statement_duration", Integer.class));

        setTableLockSettings(SCHEMA, TABLE, 10, 20, 30);
        Record updated = getTableLockSettings(SCHEMA, TABLE);
        assertNotNull(updated);
        assertEquals(10, updated.get("ddl_lock_timeout", Integer.class));
        assertEquals(20, updated.get("sleep_time", Integer.class));
        assertEquals(30, updated.get("statement_duration", Integer.class));
    }

    /**
     * set_table_lock_settings rejects a null schema or table name and null or
     * negative values through the domains.
     */
    @Test
    void setTableLockSettingsRejectsInvalidValues() {
        assertDomainViolation(() -> setTableLockSettings(null, TABLE, 100, 1000, 30));
        assertDomainViolation(() -> setTableLockSettings(SCHEMA, null, 100, 1000, 30));
        assertDomainViolation(() -> setTableLockSettings(SCHEMA, TABLE, null, 1000, 30));
        assertDomainViolation(() -> setTableLockSettings(SCHEMA, TABLE, 100, null, 30));
        assertDomainViolation(() -> setTableLockSettings(SCHEMA, TABLE, 100, 1000, null));
        assertDomainViolation(() -> setTableLockSettings(SCHEMA, TABLE, -1, 1000, 30));
        assertDomainViolation(() -> setTableLockSettings(SCHEMA, TABLE, 100, -1, 30));
        assertDomainViolation(() -> setTableLockSettings(SCHEMA, TABLE, 100, 1000, -1));
    }

    /**
     * The caller may read the table but not modify it: UPDATE is denied with an
     * insufficient-privilege error.
     */
    @Test
    void callerCannotUpdateTableLockSettings() {
        assertSqlState("42501", () -> dsl.execute(
                "UPDATE ddl_utils.table_lock_settings SET sleep_time = 1 WHERE schema_name = 'x'"));
    }

    /**
     * clear_table_lock_settings deletes the table's row, and is a no-op when
     * there is none.
     */
    @Test
    void clearTableLockSettingsDeletesRow() {
        setTableLockSettings(SCHEMA, CLEAR_TABLE, 111, 222, 33);
        assertNotNull(getTableLockSettings(SCHEMA, CLEAR_TABLE));

        clearTableLockSettings(SCHEMA, CLEAR_TABLE);
        assertNull(getTableLockSettings(SCHEMA, CLEAR_TABLE));

        assertDoesNotThrow(() -> clearTableLockSettings(SCHEMA, CLEAR_TABLE));
    }

    /**
     * clear_table_lock_settings rejects a null schema or table name through the
     * domains.
     */
    @Test
    void clearTableLockSettingsRejectsNullArguments() {
        assertDomainViolation(() -> clearTableLockSettings(null, TABLE));
        assertDomainViolation(() -> clearTableLockSettings(SCHEMA, null));
    }

    private void setTableLockSettings(String schema, String table, Integer lockTimeout,
                                      Integer sleepTime, Integer duration) {
        Routines.setTableLockSettings(dsl.configuration(), schema, table, lockTimeout, sleepTime, duration);
    }

    private void clearTableLockSettings(String schema, String table) {
        Routines.clearTableLockSettings(dsl.configuration(), schema, table);
    }

    private Record getTableLockSettings(String schema, String table) {
        return dsl.fetchOne("""
                SELECT ddl_lock_timeout, sleep_time, statement_duration
                FROM ddl_utils.get_table_lock_settings(?, ?)
                """, schema, table);
    }
}

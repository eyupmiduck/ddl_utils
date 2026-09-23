package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    private static final String KEEP_TABLE = "table_lock_settings_keep_table";

    /**
     * Removes any rows this class created, so its tests stay order-independent.
     */
    @AfterEach
    void clearSeededRows() {
        clearTableLockSettings(SCHEMA, TABLE);
        clearTableLockSettings(SCHEMA, CLEAR_TABLE);
        clearTableLockSettings(SCHEMA, KEEP_TABLE);
    }

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
        assertLockSettings(inserted, 200, 300, 40);

        setTableLockSettings(SCHEMA, TABLE, 10, 20, 30);
        Record updated = getTableLockSettings(SCHEMA, TABLE);
        assertNotNull(updated);
        assertLockSettings(updated, 10, 20, 30);
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
     * The caller may read the table but not modify it: UPDATE, INSERT and
     * DELETE are all denied with an insufficient-privilege error.
     */
    @Test
    void callerCannotWriteTableLockSettings() {
        assertSqlState("42501", () -> dsl.execute(
                "UPDATE ddl_utils.table_lock_settings SET sleep_time = 1 WHERE schema_name = 'x'"));
        assertSqlState("42501", () -> dsl.execute("""
                INSERT INTO ddl_utils.table_lock_settings (
                    schema_name, table_name, ddl_lock_timeout, sleep_time, statement_duration
                )
                VALUES ('x', 'y', 1, 1, 1)
                """));
        assertSqlState("42501", () -> dsl.execute(
                "DELETE FROM ddl_utils.table_lock_settings WHERE schema_name = 'x'"));
    }

    /**
     * clear_table_lock_settings deletes only the target table's row, leaves
     * other rows alone, and is a no-op when there is none.
     */
    @Test
    void clearTableLockSettingsDeletesRow() {
        setTableLockSettings(SCHEMA, KEEP_TABLE, 200, 300, 40);
        setTableLockSettings(SCHEMA, CLEAR_TABLE, 111, 222, 33);

        clearTableLockSettings(SCHEMA, CLEAR_TABLE);

        assertNull(getTableLockSettings(SCHEMA, CLEAR_TABLE));
        assertNotNull(getTableLockSettings(SCHEMA, KEEP_TABLE));

        clearTableLockSettings(SCHEMA, KEEP_TABLE);
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

}

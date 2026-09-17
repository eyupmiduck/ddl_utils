package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.Routines;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@code ddl_utils.schema_lock_settings} and its accessors: the caller
 * can read the table, {@code get_schema_lock_settings} returns a schema's row,
 * and {@code set_schema_lock_settings} upserts it (SECURITY DEFINER, since the
 * caller has only SELECT).
 */
class SchemaLockSettingsTest extends PostgresTestBase {

    private static final String SCHEMA = "lock_settings_test_schema";
    private static final String CLEAR_SCHEMA = "lock_settings_clear_schema";

    /**
     * get_schema_lock_settings returns no row for a schema that has no settings.
     */
    @Test
    void getSchemaLockSettingsReturnsNothingForUnknownSchema() {
        assertNull(getSchemaLockSettings("lock_settings_absent_schema"));
    }

    /**
     * set_schema_lock_settings inserts a row for a new schema and updates it on
     * a second call, with get_schema_lock_settings reflecting both.
     */
    @Test
    void setSchemaLockSettingsInsertsAndUpdates() {
        setSchemaLockSettings(SCHEMA, 200, 300, 40);
        Record inserted = getSchemaLockSettings(SCHEMA);
        assertNotNull(inserted);
        assertEquals(200, inserted.get("ddl_lock_timeout", Integer.class));
        assertEquals(300, inserted.get("sleep_time", Integer.class));
        assertEquals(40, inserted.get("statement_duration", Integer.class));

        setSchemaLockSettings(SCHEMA, 10, 20, 30);
        Record updated = getSchemaLockSettings(SCHEMA);
        assertNotNull(updated);
        assertEquals(10, updated.get("ddl_lock_timeout", Integer.class));
        assertEquals(20, updated.get("sleep_time", Integer.class));
        assertEquals(30, updated.get("statement_duration", Integer.class));
    }

    /**
     * set_schema_lock_settings rejects a null schema name and null or negative
     * values through the domains.
     */
    @Test
    void setSchemaLockSettingsRejectsInvalidValues() {
        assertDomainViolation(() -> setSchemaLockSettings(null, 100, 1000, 30));
        assertDomainViolation(() -> setSchemaLockSettings(SCHEMA, null, 1000, 30));
        assertDomainViolation(() -> setSchemaLockSettings(SCHEMA, 100, null, 30));
        assertDomainViolation(() -> setSchemaLockSettings(SCHEMA, 100, 1000, null));
        assertDomainViolation(() -> setSchemaLockSettings(SCHEMA, -1, 1000, 30));
        assertDomainViolation(() -> setSchemaLockSettings(SCHEMA, 100, -1, 30));
        assertDomainViolation(() -> setSchemaLockSettings(SCHEMA, 100, 1000, -1));
    }

    /**
     * The caller may read the table but not modify it: UPDATE is denied with an
     * insufficient-privilege error.
     */
    @Test
    void callerCannotUpdateSchemaLockSettings() {
        assertSqlState("42501", () -> dsl.execute(
                "UPDATE ddl_utils.schema_lock_settings SET sleep_time = 1 WHERE schema_name = 'x'"));
    }

    /**
     * clear_schema_lock_settings deletes the schema's row, and is a no-op when
     * there is none.
     */
    @Test
    void clearSchemaLockSettingsDeletesRow() {
        setSchemaLockSettings(CLEAR_SCHEMA, 111, 222, 33);
        assertNotNull(getSchemaLockSettings(CLEAR_SCHEMA));

        clearSchemaLockSettings(CLEAR_SCHEMA);
        assertNull(getSchemaLockSettings(CLEAR_SCHEMA));

        assertDoesNotThrow(() -> clearSchemaLockSettings(CLEAR_SCHEMA));
    }

    /**
     * clear_schema_lock_settings rejects a null schema name through the domain.
     */
    @Test
    void clearSchemaLockSettingsRejectsNullSchema() {
        assertDomainViolation(() -> clearSchemaLockSettings(null));
    }

    private void clearSchemaLockSettings(String schema) {
        Routines.clearSchemaLockSettings(dsl.configuration(), schema);
    }

    private void setSchemaLockSettings(String schema, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.setSchemaLockSettings(dsl.configuration(), schema, lockTimeout, sleepTime, duration);
    }

    private Record getSchemaLockSettings(String schema) {
        return dsl.fetchOne("""
                SELECT ddl_lock_timeout, sleep_time, statement_duration
                FROM ddl_utils.get_schema_lock_settings(?)
                """, schema);
    }
}

package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the fallback order of {@code ddl_utils.get_lock_settings(schema,
 * table)}: a table override wins over a schema override, which wins over the
 * database defaults, and an exception is raised when no settings exist at all.
 */
class GetLockSettingsTest extends PostgresTestBase {

    private static final String SCHEMA = "get_lock_settings_test_schema";
    private static final String TABLE = "get_lock_settings_test_table";
    private static final int DEFAULT_DDL_LOCK_TIMEOUT = 100;
    private static final int DEFAULT_SLEEP_TIME = 1000;
    private static final int DEFAULT_STATEMENT_DURATION = 30000;

    /**
     * Clears the schema and table overrides and resets the database defaults,
     * so each test starts from a known state.
     */
    @BeforeEach
    void resetLockSettings() {
        setDatabaseLockSettings(DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
        clearSchemaLockSettings(SCHEMA);
        clearTableLockSettings(SCHEMA, TABLE);
    }

    /**
     * With no table or schema override, get_lock_settings returns the database
     * defaults.
     */
    @Test
    void fallsBackToDatabaseDefaultsWhenNoOverrideExists() {
        Record settings = getLockSettings(SCHEMA, TABLE);

        assertNotNull(settings);
        assertLockSettings(settings, DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
    }

    /**
     * A schema override takes precedence over the database defaults.
     */
    @Test
    void schemaOverrideWinsOverDatabaseDefaults() {
        setSchemaLockSettings(SCHEMA, 200, 300, 40);

        Record settings = getLockSettings(SCHEMA, TABLE);

        assertNotNull(settings);
        assertLockSettings(settings, 200, 300, 40);
    }

    /**
     * A table override takes precedence over both a schema override and the
     * database defaults.
     */
    @Test
    void tableOverrideWinsOverSchemaAndDatabase() {
        setSchemaLockSettings(SCHEMA, 200, 300, 40);
        setTableLockSettings(SCHEMA, TABLE, 10, 20, 30);

        Record settings = getLockSettings(SCHEMA, TABLE);

        assertNotNull(settings);
        assertLockSettings(settings, 10, 20, 30);
    }

    /**
     * When neither the table, the schema, nor the database has settings,
     * get_lock_settings raises a no_data_found error.
     */
    @Test
    void raisesWhenNoSettingsExistAnywhere() {
        deleteDatabaseDefaults();
        try {
            assertSqlState("P0002", () -> getLockSettings(SCHEMA, TABLE));
        } finally {
            restoreDatabaseDefaults(DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
        }
    }

    /**
     * get_database_lock_settings raises no_data_found when the singleton row is
     * missing, rather than returning an empty result.
     */
    @Test
    void getDatabaseLockSettingsRaisesWhenRowMissing() {
        deleteDatabaseDefaults();
        try {
            assertSqlState("P0002",
                    () -> dsl.fetch("SELECT * FROM ddl_utils.get_database_lock_settings()"));
        } finally {
            restoreDatabaseDefaults(DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
        }
    }
}

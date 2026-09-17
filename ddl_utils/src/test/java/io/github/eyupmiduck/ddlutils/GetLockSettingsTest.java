package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Routines.setDatabaseLockSettings(dsl.configuration(),
                DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
        Routines.clearSchemaLockSettings(dsl.configuration(), SCHEMA);
        Routines.clearTableLockSettings(dsl.configuration(), SCHEMA, TABLE);
    }

    /**
     * With no table or schema override, get_lock_settings returns the database
     * defaults.
     */
    @Test
    void fallsBackToDatabaseDefaultsWhenNoOverrideExists() {
        Record settings = getLockSettings(SCHEMA, TABLE);

        assertNotNull(settings);
        assertEquals(DEFAULT_DDL_LOCK_TIMEOUT, settings.get("ddl_lock_timeout", Integer.class));
        assertEquals(DEFAULT_SLEEP_TIME, settings.get("sleep_time", Integer.class));
        assertEquals(DEFAULT_STATEMENT_DURATION, settings.get("statement_duration", Integer.class));
    }

    /**
     * A schema override takes precedence over the database defaults.
     */
    @Test
    void schemaOverrideWinsOverDatabaseDefaults() {
        Routines.setSchemaLockSettings(dsl.configuration(), SCHEMA, 200, 300, 40);

        Record settings = getLockSettings(SCHEMA, TABLE);

        assertNotNull(settings);
        assertEquals(200, settings.get("ddl_lock_timeout", Integer.class));
        assertEquals(300, settings.get("sleep_time", Integer.class));
        assertEquals(40, settings.get("statement_duration", Integer.class));
    }

    /**
     * A table override takes precedence over both a schema override and the
     * database defaults.
     */
    @Test
    void tableOverrideWinsOverSchemaAndDatabase() {
        Routines.setSchemaLockSettings(dsl.configuration(), SCHEMA, 200, 300, 40);
        Routines.setTableLockSettings(dsl.configuration(), SCHEMA, TABLE, 10, 20, 30);

        Record settings = getLockSettings(SCHEMA, TABLE);

        assertNotNull(settings);
        assertEquals(10, settings.get("ddl_lock_timeout", Integer.class));
        assertEquals(20, settings.get("sleep_time", Integer.class));
        assertEquals(30, settings.get("statement_duration", Integer.class));
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
            restoreDatabaseDefaults();
        }
    }

    private Record getLockSettings(String schema, String table) {
        return dsl.fetchOne("""
                SELECT ddl_lock_timeout, sleep_time, statement_duration
                FROM ddl_utils.get_lock_settings(?, ?)
                """, schema, table);
    }

    private void deleteDatabaseDefaults() {
        try (Connection connection = openOwnerConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM ddl_utils.database_lock_settings WHERE id = 1");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to delete the database defaults", e);
        }
    }

    private void restoreDatabaseDefaults() {
        try (Connection connection = openOwnerConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO ddl_utils.database_lock_settings (
                        id, ddl_lock_timeout, sleep_time, statement_duration
                    )
                    VALUES (1, %d, %d, %d)
                    ON CONFLICT (id) DO UPDATE
                        SET ddl_lock_timeout   = EXCLUDED.ddl_lock_timeout,
                            sleep_time         = EXCLUDED.sleep_time,
                            statement_duration = EXCLUDED.statement_duration
                    """.formatted(DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to restore the database defaults", e);
        }
    }
}

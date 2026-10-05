package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@code ddl_utils.database_lock_settings} and its accessors: the single row is
 * readable by the caller, {@code get_database_lock_settings} returns it, and
 * {@code set_database_lock_settings} updates it (SECURITY DEFINER, since the caller has
 * only SELECT on the table).
 */
class LockSettingsTest extends PostgresTestBase {

    private static final int DEFAULT_DDL_LOCK_TIMEOUT = 100;
    private static final int DEFAULT_SLEEP_TIME = 1000;
    private static final int DEFAULT_STATEMENT_DURATION = 30000;

    /**
     * Resets the row to the seeded defaults after every test, so tests are
     * independent of each other's updates. Cleanup runs after the test rather
     * than before it so {@link #lockSettingsRowIsSeeded} observes the values the
     * migration actually seeded, not a value this class wrote.
     */
    @AfterEach
    void resetLockSettings() {
        setDatabaseLockSettings(DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
    }

    /**
     * The lock settings table holds the seeded row.
     */
    @Test
    void lockSettingsRowIsSeeded() {
        Record row = tableRow();

        assertNotNull(row);
        assertLockSettings(row, DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
    }

    /**
     * The caller may read the table but not modify it: SELECT succeeds while
     * UPDATE, INSERT and DELETE are denied with an insufficient-privilege error.
     */
    @Test
    void callerCanSelectButNotWriteLockSettings() {
        assertNotNull(tableRow());

        assertSqlState("42501",
                () -> dsl.execute("UPDATE ddl_utils.database_lock_settings SET sleep_time = 1 WHERE id = 1"));
        assertSqlState("42501", () -> dsl.execute("""
                INSERT INTO ddl_utils.database_lock_settings (id, ddl_lock_timeout, sleep_time, statement_duration)
                VALUES (1, 1, 1, 1)
                """));
        assertSqlState("42501",
                () -> dsl.execute("DELETE FROM ddl_utils.database_lock_settings WHERE id = 1"));
    }

    /**
     * The table is a singleton: it holds exactly one row, and a second row is
     * rejected by the fixed-id check even for the owner (who has write
     * privileges).
     */
    @Test
    void databaseLockSettingsIsSingleton() throws Exception {
        assertEquals(1, dsl.fetchOne("SELECT count(*)::int FROM ddl_utils.database_lock_settings")
                .get(0, Integer.class));

        try (Connection owner = openOwnerConnection();
             Statement statement = owner.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO ddl_utils.database_lock_settings (id, ddl_lock_timeout, sleep_time, statement_duration)
                    VALUES (2, 1, 1, 1)
                    """));
            assertEquals("23514", failure.getSQLState());
        }
    }

    /**
     * get_database_lock_settings returns the current values.
     */
    @Test
    void getLockSettingsReturnsCurrentValues() {
        Record row = getDatabaseLockSettings();

        assertNotNull(row);
        assertLockSettings(row, DEFAULT_DDL_LOCK_TIMEOUT, DEFAULT_SLEEP_TIME, DEFAULT_STATEMENT_DURATION);
    }

    /**
     * set_database_lock_settings updates the row, and the change is visible to both the
     * table and get_database_lock_settings.
     */
    @Test
    void setLockSettingsUpdatesValues() {
        setDatabaseLockSettings(250, 500, 60);

        assertLockSettings(tableRow(), 250, 500, 60);
        assertLockSettings(getDatabaseLockSettings(), 250, 500, 60);
    }

    /**
     * The documented lower boundary zero is accepted for every setting.
     */
    @Test
    void setLockSettingsAcceptsZero() {
        setDatabaseLockSettings(0, 0, 0);

        assertLockSettings(tableRow(), 0, 0, 0);
        assertLockSettings(getDatabaseLockSettings(), 0, 0, 0);
    }

    /**
     * set_database_lock_settings rejects null and negative values through the
     * non_negative_integer domain.
     */
    @Test
    void setLockSettingsRejectsInvalidValues() {
        assertDomainViolation(() -> setDatabaseLockSettings(null, 1000, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, null, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, 1000, null));
        assertDomainViolation(() -> setDatabaseLockSettings(-1, 1000, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, -1, 30));
        assertDomainViolation(() -> setDatabaseLockSettings(100, 1000, -1));
    }

    private Record tableRow() {
        return dsl.fetchOne("""
                SELECT ddl_lock_timeout, sleep_time, statement_duration
                FROM ddl_utils.database_lock_settings
                WHERE id = 1
                """);
    }

}

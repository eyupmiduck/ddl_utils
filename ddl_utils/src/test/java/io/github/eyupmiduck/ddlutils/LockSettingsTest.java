package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
     * Resets the row to the seeded defaults, so tests are independent of each
     * other's updates.
     */
    @BeforeEach
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
     * UPDATE is denied with an insufficient-privilege error.
     */
    @Test
    void callerCanSelectButNotUpdateLockSettings() {
        assertNotNull(tableRow());

        assertSqlState("42501",
                () -> dsl.execute("UPDATE ddl_utils.database_lock_settings SET sleep_time = 1 WHERE id = 1"));
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

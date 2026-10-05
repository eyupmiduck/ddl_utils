package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

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
        assertLockSettings(inserted, 200, 300, 40);

        setSchemaLockSettings(SCHEMA, 10, 20, 30);
        Record updated = getSchemaLockSettings(SCHEMA);
        assertNotNull(updated);
        assertLockSettings(updated, 10, 20, 30);
    }

    /**
     * set_schema_lock_settings rejects a null schema name and null or negative
     * values through the domains.
     */
    @Test
    void setSchemaLockSettingsRejectsInvalidValues() {
        assertSqlState("23514", () -> setSchemaLockSettings(null, 100, 1000, 30));
        assertSqlState("23514", () -> setSchemaLockSettings(SCHEMA, null, 1000, 30));
        assertSqlState("23514", () -> setSchemaLockSettings(SCHEMA, 100, null, 30));
        assertSqlState("23514", () -> setSchemaLockSettings(SCHEMA, 100, 1000, null));
        assertSqlState("23514", () -> setSchemaLockSettings(SCHEMA, -1, 1000, 30));
        assertSqlState("23514", () -> setSchemaLockSettings(SCHEMA, 100, -1, 30));
        assertSqlState("23514", () -> setSchemaLockSettings(SCHEMA, 100, 1000, -1));
    }

    /**
     * Concurrent first-time writers for the same schema all complete without a
     * unique violation or deadlock, and the final row is one writer's complete
     * value set (the three columns stay consistent), so the upsert is atomic.
     */
    @Test
    void concurrentFirstWritesLeaveAConsistentRow() throws Exception {
        int writers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CyclicBarrier barrier = new CyclicBarrier(writers);
        List<Future<?>> futures = new ArrayList<>();
        Record row = null;
        try {
            for (int i = 0; i < writers; i++) {
                int offset = i;
                futures.add(pool.submit(() -> {
                    barrier.await();
                    try (Connection connection = openTestConnection();
                         PreparedStatement statement = connection.prepareStatement(
                                 "SELECT ddl_utils.set_schema_lock_settings(?, ?, ?, ?)")) {
                        statement.setString(1, SCHEMA);
                        statement.setInt(2, 100 + offset);
                        statement.setInt(3, 200 + offset);
                        statement.setInt(4, 300 + offset);
                        statement.execute();
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            row = getSchemaLockSettings(SCHEMA);
        } finally {
            pool.shutdownNow();
            clearSchemaLockSettings(SCHEMA);
        }

        assertNotNull(row);
        int ddl = row.get("ddl_lock_timeout", Integer.class);
        assertEquals(ddl + 100, row.get("sleep_time", Integer.class),
                "the row must be one writer's complete value set");
        assertEquals(ddl + 200, row.get("statement_duration", Integer.class),
                "the row must be one writer's complete value set");
    }

    /**
     * The caller may read the table but not modify it: UPDATE is denied with an
     * insufficient-privilege error.
     */
    @Test
    void callerCannotUpdateSchemaLockSettings() {
        setSchemaLockSettings(SCHEMA, 200, 300, 40);

        assertSqlState("42501", () -> dsl.execute(
                "UPDATE ddl_utils.schema_lock_settings SET sleep_time = 1 WHERE schema_name = ?", SCHEMA));
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

}

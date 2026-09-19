package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the project-wide audit-column standard on the lock-settings tables:
 * every table has {@code created_at}/{@code updated_at} ({@code timestamptz NOT
 * NULL DEFAULT now()}), and {@code ddl_utils.set_updated_at()} refreshes
 * {@code updated_at} on every {@code UPDATE} so a caller cannot bypass or forge
 * it.
 */
class AuditColumnsTest extends PostgresTestBase {

    private static final List<String> TABLES =
            List.of("database_lock_settings", "schema_lock_settings", "table_lock_settings");
    private static final String SCHEMA = "audit_columns_test_schema";
    private static final OffsetDateTime FAR_PAST = OffsetDateTime.parse("2000-01-01T00:00:00Z");

    /**
     * Every lock-settings table carries {@code created_at} and {@code updated_at}
     * as {@code timestamp with time zone NOT NULL DEFAULT now()}.
     */
    @Test
    void lockSettingsTablesHaveTimestamptzAuditColumns() {
        for (String table : TABLES) {
            for (String column : List.of("created_at", "updated_at")) {
                assertEquals("timestamp with time zone",
                        columnAttribute("ddl_utils", table, column, "data_type"),
                        table + "." + column + " must be timestamptz");
                assertEquals("NO",
                        columnAttribute("ddl_utils", table, column, "is_nullable"),
                        table + "." + column + " must be NOT NULL");
                assertEquals("now()",
                        columnAttribute("ddl_utils", table, column, "column_default"),
                        table + "." + column + " must default to now()");
            }
        }
    }

    /**
     * An {@code UPDATE} through the trigger overwrites a caller-supplied
     * {@code updated_at} with the transaction timestamp, while {@code created_at}
     * is left untouched.
     */
    @Test
    void updateRefreshesUpdatedAtAndPreservesCreatedAt() throws SQLException {
        setSchemaLockSettings(SCHEMA, 100, 1000, 30);
        Record before = schemaAuditRow(SCHEMA);
        OffsetDateTime createdAt = before.get("created_at", OffsetDateTime.class);

        // The owner bypasses the caller's lack of UPDATE, and the bogus value
        // proves the trigger stamps updated_at regardless of what is supplied.
        try (Connection owner = openOwnerConnection();
             Statement statement = owner.createStatement()) {
            statement.execute("""
                    UPDATE ddl_utils.schema_lock_settings
                    SET sleep_time = sleep_time, updated_at = '2000-01-01T00:00:00+00'
                    WHERE schema_name = '%s'
                    """.formatted(SCHEMA));
        }

        Record after = schemaAuditRow(SCHEMA);
        OffsetDateTime updatedAt = after.get("updated_at", OffsetDateTime.class);
        assertNotEquals(FAR_PAST, updatedAt, "the trigger must overwrite a caller-supplied updated_at");
        assertTrue(updatedAt.isAfter(FAR_PAST), "updated_at must be refreshed to now()");
        assertEquals(createdAt, after.get("created_at", OffsetDateTime.class),
                "created_at must not change on UPDATE");
    }

    private Record schemaAuditRow(String schema) {
        return dsl.fetchOne("""
                SELECT created_at, updated_at
                FROM ddl_utils.schema_lock_settings
                WHERE schema_name = ?
                """, schema);
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.AuditColumnsCheck;
import io.github.eyupmiduck.changelogvalidator.testing.RoutineAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Applies the shared {@link AuditColumnsCheck} to the {@code ddl_utils} schema,
 * verifying that the lock-settings tables follow the project-wide audit-column
 * convention: {@code created_at}/{@code updated_at} are {@code timestamptz NOT
 * NULL DEFAULT now()}, an enabled {@code BEFORE UPDATE} row trigger refreshes
 * {@code updated_at}, and the trigger preserves {@code created_at}.
 */
class AuditColumnsTest extends PostgresTestBase {

    /**
     * The probe schema and table whose lock-settings rows seed the behavioral
     * probe. The seed and the cleanup share these constants so a drifted
     * cleanup cannot silently leave rows behind.
     */
    private static final String PROBE_SCHEMA = "audit_columns_test_schema";
    private static final String PROBE_TABLE = "audit_columns_test_table";

    /**
     * Removes the rows the behavioral probe seeds, even when the test fails.
     * Both cleanups are attempted; the first failure is preserved and the second
     * is attached as suppressed.
     */
    @AfterEach
    void clearAuditFixtures() {
        RuntimeException failure = null;
        try {
            clearTableLockSettings(PROBE_SCHEMA, PROBE_TABLE);
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            clearSchemaLockSettings(PROBE_SCHEMA);
        } catch (RuntimeException e) {
            if (failure != null) {
                failure.addSuppressed(e);
            } else {
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Every table in the {@code ddl_utils} schema satisfies the catalog
     * convention, so {@link AuditColumnsCheck#findViolations} reports nothing.
     */
    @Test
    void lockSettingsTablesFollowTheAuditColumnConvention() throws SQLException {
        try (Connection owner = openOwnerConnection()) {
            RoutineAssertions.assertAuditColumnsFollowConvention(owner, List.of("ddl_utils"));
        }
    }

    /**
     * The behavioral probe confirms the shared trigger refreshes
     * {@code updated_at} and preserves {@code created_at} on a real row, for
     * each lock-settings table. The probe rolls its update back.
     */
    @Test
    void updateTriggerRefreshesUpdatedAtAndPreservesCreatedAt() throws SQLException {
        setSchemaLockSettings(PROBE_SCHEMA, 100, 1000, 30);
        setTableLockSettings(PROBE_SCHEMA, PROBE_TABLE, 100, 1000, 30);
        try (Connection owner = openOwnerConnection()) {
            RoutineAssertions.assertUpdateTriggerRefreshesAuditColumns(owner, "ddl_utils",
                    List.of("database_lock_settings", "schema_lock_settings", "table_lock_settings"));
        }
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.AuditColumnsCheck;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies the shared {@link AuditColumnsCheck} to the {@code ddl_utils} schema,
 * verifying that the lock-settings tables follow the project-wide audit-column
 * convention: {@code created_at}/{@code updated_at} are {@code timestamptz NOT
 * NULL DEFAULT now()}, an enabled {@code BEFORE UPDATE} row trigger refreshes
 * {@code updated_at}, and the trigger preserves {@code created_at}.
 */
class AuditColumnsTest extends PostgresTestBase {

    /**
     * Every table in the {@code ddl_utils} schema satisfies the catalog
     * convention, so {@link AuditColumnsCheck#findViolations} reports nothing.
     */
    @Test
    void lockSettingsTablesFollowTheAuditColumnConvention() throws SQLException {
        try (Connection owner = openOwnerConnection()) {
            List<AuditColumnsCheck.Violation> violations =
                    AuditColumnsCheck.findViolations(owner, List.of("ddl_utils"));

            assertEquals(List.of(), violations.stream()
                            .map(AuditColumnsCheck.Violation::describe)
                            .collect(Collectors.toList()),
                    "the ddl_utils schema must follow the audit-column convention");
        }
    }

    /**
     * The behavioral probe confirms the shared trigger refreshes
     * {@code updated_at} and preserves {@code created_at} on a real row, for
     * each lock-settings table. The probe rolls its update back.
     */
    @Test
    void updateTriggerRefreshesUpdatedAtAndPreservesCreatedAt() throws SQLException {
        setSchemaLockSettings("audit_columns_test_schema", 100, 1000, 30);
        setTableLockSettings("audit_columns_test_schema", "audit_columns_test_table", 100, 1000, 30);
        try (Connection owner = openOwnerConnection()) {
            for (String table : List.of("database_lock_settings", "schema_lock_settings", "table_lock_settings")) {
                AuditColumnsCheck.Probe probe = AuditColumnsCheck.probeUpdate(owner, "ddl_utils", table)
                        .orElseThrow(() -> new AssertionError("no row to probe in ddl_utils." + table));

                assertTrue(probe.passed(), probe::describe);
            }
        } finally {
            // The seeded rows above are committed and shared with the other test
            // in this class, so remove them.
            clearTableLockSettings("audit_columns_test_schema", "audit_columns_test_table");
            clearSchemaLockSettings("audit_columns_test_schema");
        }
    }
}

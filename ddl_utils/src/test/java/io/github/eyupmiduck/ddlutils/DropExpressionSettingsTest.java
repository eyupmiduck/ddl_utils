package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware {@code ddl_utils.drop_expression} wrapper: it resolves
 * the table's lock settings through {@code get_lock_settings} and delegates to
 * the {@code ddl_utils_lib} helper.
 */
class DropExpressionSettingsTest extends PostgresTestBase {

    private static final String TARGET = "drop_expression_settings_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "a int, b int GENERATED ALWAYS AS (a * 2) STORED");
    }

    @AfterEach
    void cleanUp() {
        try {
            clearTableLockSettings(PUBLIC_SCHEMA, TARGET);
        } finally {
            dropTestTable(TARGET);
        }
    }

    /**
     * Drops the generated expression using the database defaults.
     */
    @Test
    void dropExpressionUsesDatabaseDefaults() {
        Routines.dropExpression(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "b");

        assertFalse(isGenerated("b"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held ACCESS SHARE lock makes the call give up quickly.
     */
    @Test
    void dropExpressionUsesTableLockSettings() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.dropExpression(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "b"));

        assertTrue(isGenerated("b"));
    }

    private boolean isGenerated(String column) {
        return dsl.fetchOne(
                """
                        SELECT a.attgenerated <> ''
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, Boolean.class);
    }
}

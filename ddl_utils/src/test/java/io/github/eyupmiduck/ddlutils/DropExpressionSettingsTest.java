package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware {@code ddl_utils.drop_expression} wrapper: it resolves
 * the table's lock settings through {@code get_lock_settings} and delegates to
 * the {@code ddl_utils_lib} helper.
 */
class DropExpressionSettingsTest extends SingleTableTest {

    DropExpressionSettingsTest() {
        super("drop_expression_settings_target", "a int, b int GENERATED ALWAYS AS (a * 2) STORED", true);
    }

    /**
     * Drops the generated expression using the database defaults.
     */
    @Test
    void dropExpressionUsesDatabaseDefaults() {
        Routines.dropExpression(dsl.configuration(), PUBLIC_SCHEMA, target(), "b");

        assertFalse(isGenerated(PUBLIC_SCHEMA, target(), "b"));
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held ACCESS SHARE lock makes the call give up quickly.
     */
    @Test
    void dropExpressionUsesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(
                () -> Routines.dropExpression(dsl.configuration(), PUBLIC_SCHEMA, target(), "b"));

        assertTrue(isGenerated(PUBLIC_SCHEMA, target(), "b"));
    }

}

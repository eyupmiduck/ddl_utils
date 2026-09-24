package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware {@code ddl_utils.add_column} and
 * {@code ddl_utils.add_columns} wrappers: they resolve lock settings through
 * {@code ddl_utils.get_lock_settings} and delegate the actual DDL to the
 * {@code ddl_utils_lib} helpers.
 */
class AddColumnSettingsTest extends SingleTableTest {

    AddColumnSettingsTest() {
        super("add_column_settings_target", "id int", true);
    }

    /**
     * add_column adds the column using the database defaults when no table or
     * schema override exists.
     */
    @Test
    void addColumnUsesDatabaseDefaults() {
        addColumn("note", "text", "'none'", false);

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "note"));
        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertColumnDefault(PUBLIC_SCHEMA, target(), "note", "'none'");
    }

    /**
     * add_column can omit the default value, falling back to the argument's
     * NULL default. jOOQ does not expose overloads for defaulted routine
     * parameters, so this calls the function directly.
     */
    @Test
    void addColumnOmitsDefaultWhenNotGiven() {
        dsl.execute("SELECT ddl_utils.add_column(?, ?, ?, ?, ?)",
                PUBLIC_SCHEMA, target(), "blank", "int", true);

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "blank"));
        assertNullable(PUBLIC_SCHEMA, target(), "blank");
        assertNoColumnDefault(PUBLIC_SCHEMA, target(), "blank");
    }

    /**
     * add_columns adds several columns in one call using the database defaults.
     */
    @Test
    void addColumnsUsesDatabaseDefaults() {
        addColumns(
                new String[]{"first", "second"},
                new String[]{"int", "text"},
                new String[]{null, null},
                new Boolean[]{true, false});

        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "first"));
        assertTrue(hasColumn(PUBLIC_SCHEMA, target(), "second"));
        assertNullable(PUBLIC_SCHEMA, target(), "first");
        assertNoColumnDefault(PUBLIC_SCHEMA, target(), "first");
        assertNotNullable(PUBLIC_SCHEMA, target(), "second");
    }

    /**
     * The wrapper passes the table-level lock settings to the helper: the
     * table's statement budget is far below the database default (30000 ms), so
     * a held lock makes the call give up quickly, whereas the default would have
     * retried for ~30 s.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        assertUsesTableLockSettings(() -> addColumn("blocked", "int", null, true));

        assertFalse(hasColumn(PUBLIC_SCHEMA, target(), "blocked"));
    }

    /**
     * add_column rejects null text and nullable arguments through the domains
     * before the body runs.
     */
    @Test
    void addColumnRejectsNullArguments() {
        assertDomainViolation(() -> addColumn(null, "text", null, true));
        assertDomainViolation(() -> addColumn("note", null, null, true));
        assertDomainViolation(() -> addColumn("note", "text", null, null));
    }

    /**
     * add_columns rejects empty arrays through its array domains before the
     * body runs.
     */
    @Test
    void addColumnsRejectsEmptyArraysThroughDomains() {
        assertDomainViolation(() -> addColumns(
                new String[0], new String[0], new String[0], new Boolean[0]));
    }

    private void addColumn(String column, String type, String defaultValue, Boolean nullable) {
        Routines.addColumn(dsl.configuration(), PUBLIC_SCHEMA, target(), column, type, nullable, defaultValue);
    }

    private void addColumns(String[] names, String[] types, String[] defaults, Boolean[] nullable) {
        Routines.addColumns(dsl.configuration(), PUBLIC_SCHEMA, target(), names, types, defaults, nullable);
    }

}

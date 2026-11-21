package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.rename_table}: it builds a
 * {@code RENAME TO} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class RenameTableTest extends PostgresTestBase {

    private static final String TARGET = "rename_table_target";
    private static final String NEW_NAME = "rename_table_renamed";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int");
    }

    @AfterEach
    void cleanUp() {
        clearTableLockSettings(PUBLIC_SCHEMA, TARGET);
        clearTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
        dropTestTable(TARGET);
        dropTestTable(NEW_NAME);
    }

    /**
     * Renames the table; data is preserved.
     */
    @Test
    void renamesTable() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id) VALUES (1)");

        renameTable(TARGET, NEW_NAME);

        assertFalse(tableExists(TARGET));
        assertTrue(tableExists(NEW_NAME));
        Record row = dsl.fetchOne("SELECT id FROM " + PUBLIC_SCHEMA + "." + NEW_NAME);
        assertNotNull(row);
        assertEquals(1, row.get(0, Integer.class), "the renamed table must preserve the original rows");
    }

    /**
     * A table-level lock-settings override moves with the table to the new name,
     * instead of being orphaned under the old name.
     */
    @Test
    void movesTableLockSettingsToNewName() {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 10, 20, 30);

        // The lock-aware wrapper (ddl_utils.rename_table) moves the settings;
        // the generic ddl_utils_lib.rename_table tested above does not.
        io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines.renameTable(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, NEW_NAME);

        assertNull(getTableLockSettings(PUBLIC_SCHEMA, TARGET),
                "the old name must not keep a stale settings row");
        Record settings = getTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
        assertNotNull(settings);
        assertLockSettings(settings, 10, 20, 30);
        assertFalse(tableExists(TARGET));
        assertTrue(tableExists(NEW_NAME));
    }

    /**
     * A pre-existing settings row for the destination is replaced by the moved
     * values (the procedure raises a NOTICE so the caller can see the collision).
     */
    @Test
    void movesTableLockSettingsToNewNameReplacingDestination() {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 10, 20, 30);
        setTableLockSettings(PUBLIC_SCHEMA, NEW_NAME, 100, 200, 300);

        io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines.renameTable(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, NEW_NAME);

        assertNull(getTableLockSettings(PUBLIC_SCHEMA, TARGET),
                "the old name must not keep a stale settings row");
        Record settings = getTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
        assertNotNull(settings);
        assertLockSettings(settings, 10, 20, 30);
        assertFalse(tableExists(TARGET));
        assertTrue(tableExists(NEW_NAME));
    }

    /**
     * Renaming a table that has no lock-settings override onto a name with a
     * pre-provisioned row leaves that row untouched: a missing source row makes
     * the settings move a no-op.
     */
    @Test
    void leavesDestinationSettingsWhenSourceHasNone() {
        setTableLockSettings(PUBLIC_SCHEMA, NEW_NAME, 100, 200, 300);

        io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines.renameTable(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, NEW_NAME);

        assertFalse(tableExists(TARGET));
        assertTrue(tableExists(NEW_NAME));
        assertNull(getTableLockSettings(PUBLIC_SCHEMA, TARGET),
                "a table without an override must not gain a settings row on rename");
        Record settings = getTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
        assertNotNull(settings);
        assertLockSettings(settings, 100, 200, 300);
    }

    /**
     * A new name longer than NAMEDATALEN - 1 bytes is rejected instead of being
     * silently truncated, and the lock settings stay with the original name.
     */
    @Test
    void rejectsOverLongNewNameWithoutMovingSettings() {
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 10, 20, 30);
        String tooLong = "n".repeat(64);

        assertSqlState("22023", () -> renameTable(TARGET, tooLong));
        assertSqlState("22023", () -> io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines.renameTable(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, tooLong));

        assertTrue(tableExists(TARGET), "a rejected rename must leave the table name in place");
        Record settings = getTableLockSettings(PUBLIC_SCHEMA, TARGET);
        assertNotNull(settings, "the original name must keep its settings");
        assertLockSettings(settings, 10, 20, 30);
        assertNull(getTableLockSettings(PUBLIC_SCHEMA, tooLong));
    }

    /**
     * A qualified new name is rejected instead of creating a table literally
     * named schema.table in the original schema.
     */
    @Test
    void rejectsQualifiedNewName() {
        assertSqlState("22023", () -> renameTable(TARGET, "other_schema." + NEW_NAME));
        assertTrue(tableExists(TARGET), "a rejected rename must leave the table name in place");

        assertSqlState("22023", () -> io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines.renameTable(
                dsl.configuration(), PUBLIC_SCHEMA, TARGET, "other_schema." + NEW_NAME));
    }

    /**
     * Rejects null arguments through their domains, including the schema and the
     * three lock settings on the generic helper.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> renameTable(null, NEW_NAME));
        assertDomainViolation(() -> renameTable(TARGET, null));
        assertDomainViolation(() -> io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines.renameTable(
                dsl.configuration(), null, TARGET, NEW_NAME));
        assertDomainViolation(() -> Routines.renameTable(dsl.configuration(), null, TARGET, NEW_NAME,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, TARGET, NEW_NAME,
                null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, TARGET, NEW_NAME,
                DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, TARGET, NEW_NAME,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    private void renameTable(String table, String newName) {
        Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, table, newName,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
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
        Integer count = dsl.fetchOne("SELECT count(*)::int FROM " + PUBLIC_SCHEMA + "." + NEW_NAME)
                .get(0, Integer.class);
        assertEquals(1, count);
    }

    /**
     * Rejects null arguments through their domains.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> renameTable(null, NEW_NAME));
        assertDomainViolation(() -> renameTable(TARGET, null));
    }

    private void renameTable(String table, String newName) {
        Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, table, newName,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

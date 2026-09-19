package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.rename_table}: it builds a
 * {@code RENAME TO} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class RenameTableTest extends PostgresTestBase {

    private static final String TARGET = "rename_table_target";
    private static final String NEW_NAME = "rename_table_renamed";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

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
        assertTrue(count == 1);
    }

    /**
     * Rejects null arguments through their domains.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> renameTable(null, NEW_NAME));
        assertDomainViolation(() -> renameTable(TARGET, null));
    }

    private boolean tableExists(String table) {
        return dsl.fetchOne(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = ?
                        )
                        """,
                PUBLIC_SCHEMA, table).get(0, Boolean.class);
    }

    private void renameTable(String table, String newName) {
        Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, table, newName,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

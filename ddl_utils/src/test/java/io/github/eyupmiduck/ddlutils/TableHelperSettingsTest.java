package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lock-aware table-level wrappers
 * {@code ddl_utils.add_primary_key_using_index},
 * {@code add_unique_constraint_using_index} and {@code rename_table}: each
 * resolves the table's lock settings through {@code get_lock_settings} and
 * delegates to the {@code ddl_utils_lib} helper.
 */
class TableHelperSettingsTest extends PostgresTestBase {

    private static final String TARGET = "table_helper_settings_target";
    private static final String REFERENCED = "table_helper_settings_referenced";
    private static final String RENAMED = "table_helper_settings_renamed";

    @BeforeEach
    void createTargetTables() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(TARGET, "id int NOT NULL, code text NOT NULL, parent_id int");
    }

    @AfterEach
    void cleanUp() {
        try {
            clearTableLockSettings(PUBLIC_SCHEMA, TARGET);
        } finally {
            dropTestTable(TARGET);
            dropTestTable(REFERENCED);
            dropTestTable(RENAMED);
        }
    }

    /**
     * add_primary_key_using_index uses the database defaults.
     */
    @Test
    void addPrimaryKeyUsesDatabaseDefaults() {
        dsl.execute("CREATE UNIQUE INDEX pk_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (id)");

        Routines.addPrimaryKeyUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "pk", "pk_idx");

        assertEquals("p", constraintType("pk"));
    }

    /**
     * add_unique_constraint_using_index and add_foreign_key use the database
     * defaults.
     */
    @Test
    void addUniqueAndForeignKeyUseDatabaseDefaults() {
        dsl.execute("CREATE UNIQUE INDEX code_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (code)");
        Routines.addUniqueConstraintUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "code_uq", "code_idx");
        assertEquals("u", constraintType("code_uq"));

        Routines.addForeignKey(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "fk_parent",
                new String[]{"parent_id"}, PUBLIC_SCHEMA, REFERENCED, new String[]{"id"});
        assertTrue(constraintExists("fk_parent"));
    }

    /**
     * rename_table uses the database defaults.
     */
    @Test
    void renameTableUsesDatabaseDefaults() {
        Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, TARGET, RENAMED);

        assertTrue(tableExists(RENAMED));
    }

    /**
     * rename_table, add_primary_key_using_index and
     * add_unique_constraint_using_index pass the table-level lock settings to
     * their helpers; a held ACCESS SHARE lock makes each give up quickly.
     */
    @Test
    void usesTableLockSettings() throws Exception {
        dsl.execute("CREATE UNIQUE INDEX pk_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (id)");
        dsl.execute("CREATE UNIQUE INDEX code_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (code)");
        setTableLockSettings(PUBLIC_SCHEMA, TARGET, 100, 100, 300);

        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.renameTable(dsl.configuration(), PUBLIC_SCHEMA, TARGET, RENAMED));
        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.addPrimaryKeyUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, TARGET, "pk", "pk_idx"));
        assertGivesUpWhileTableLocked(TARGET, 2000,
                () -> Routines.addUniqueConstraintUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, TARGET,
                        "code_uq", "code_idx"));
    }

    private String constraintType(String name) {
        return dsl.fetchOne(
                """
                        SELECT contype::text FROM pg_constraint
                        WHERE conname = ? AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        """,
                name, PUBLIC_SCHEMA).get(0, String.class);
    }

    private boolean constraintExists(String name) {
        return dsl.fetchOne(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = ? AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        )
                        """,
                name, PUBLIC_SCHEMA).get(0, Boolean.class);
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
}

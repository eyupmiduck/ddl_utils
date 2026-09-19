package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.add_identity} and {@code drop_identity}: they
 * build {@code ADD/DROP GENERATED ... AS IDENTITY} fragments and apply them
 * through {@code ddl_utils_lib.alter_table}.
 */
class AddDropIdentityTest extends PostgresTestBase {

    private static final String TARGET = "add_drop_identity_target";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int NOT NULL, note text");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Adds a BY DEFAULT identity; the column gets a sequence and fills new rows
     * automatically.
     */
    @Test
    void addsIdentity() {
        addIdentity("id", "BY DEFAULT");

        assertTrue(identity("id") != null);
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (note) VALUES ('a')");
        Integer next = dsl.fetchOne("SELECT id FROM " + PUBLIC_SCHEMA + "." + TARGET).get(0, Integer.class);
        assertEquals(1, next);
    }

    /**
     * Drops the identity, leaving the column in place.
     */
    @Test
    void dropsIdentity() {
        addIdentity("id", "ALWAYS");
        dropIdentity("id", true);

        assertEquals("", identity("id"));
    }

    /**
     * DROP IDENTITY IF EXISTS tolerates a column that has no identity.
     */
    @Test
    void dropIdentityIfExistsIsNoop() {
        dropIdentity("id", true);
    }

    /**
     * Without IF EXISTS, dropping a missing identity raises an error.
     */
    @Test
    void dropIdentityWithoutIfExistsFails() {
        assertSqlState("55000", () -> dropIdentity("id", false));
    }

    /**
     * An invalid generated mode is rejected before the body runs.
     */
    @Test
    void rejectsInvalidGeneratedMode() {
        assertSqlState("22023", () -> addIdentity("id", "SOMETIMES"));
    }

    /**
     * Rejects null arguments through their domains.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> addIdentity(null, "ALWAYS"));
        assertDomainViolation(() -> addIdentity("id", null));
        assertDomainViolation(() -> dropIdentity("id", null));
    }

    private String identity(String column) {
        return dsl.fetchOne(
                """
                        SELECT a.attidentity::text
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, String.class);
    }

    private void addIdentity(String column, String generated) {
        Routines.addIdentity(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column, generated,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void dropIdentity(String column, Boolean ifExists) {
        Routines.dropIdentity(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column, ifExists,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

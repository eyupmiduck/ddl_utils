package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@code ddl_utils_lib.add_identity} and {@code drop_identity}: they
 * build {@code ADD/DROP GENERATED ... AS IDENTITY} fragments and apply them
 * through {@code ddl_utils_lib.alter_table}.
 */
class AddDropIdentityTest extends SingleTableTest {

    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    AddDropIdentityTest() {
        super("add_drop_identity_target", "id int NOT NULL, note text");
    }

    /**
     * Adds a BY DEFAULT identity; the column gets a sequence and fills new rows
     * automatically.
     */
    @Test
    void addsIdentity() {
        addIdentity("id", "BY DEFAULT");

        assertEquals("d", columnIdentity(PUBLIC_SCHEMA, target(), "id"));
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (note) VALUES ('a')");
        Integer next = dsl.fetchOne("SELECT id FROM " + PUBLIC_SCHEMA + "." + target()).get(0, Integer.class);
        assertEquals(1, next);
    }

    /**
     * Drops the identity, leaving the column in place.
     */
    @Test
    void dropsIdentity() {
        addIdentity("id", "ALWAYS");
        dropIdentity("id", true);

        assertEquals("", columnIdentity(PUBLIC_SCHEMA, target(), "id"));
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

    private void addIdentity(String column, String generated) {
        Routines.addIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), column, generated,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void dropIdentity(String column, Boolean ifExists) {
        Routines.dropIdentity(dsl.configuration(), PUBLIC_SCHEMA, target(), column, ifExists,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

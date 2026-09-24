package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code ddl_utils_lib.set_not_null}: it builds an
 * {@code ALTER COLUMN ... SET NOT NULL} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class SetNotNullTest extends SingleTableTest {

    SetNotNullTest() {
        super("set_not_null_target", "id int, note text");
    }

    /**
     * Sets NOT NULL on a nullable column without touching the others.
     */
    @Test
    void setsNotNull() {
        setNotNull("note");

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertNullable(PUBLIC_SCHEMA, target(), "id");
    }

    /**
     * Setting NOT NULL on a column that is already NOT NULL is a no-op, not an
     * error.
     */
    @Test
    void setsNotNullWhenAlreadySet() {
        setNotNull("note");
        setNotNull("note");

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
    }

    /**
     * A null value already present makes the call fail with a not-null
     * violation, leaving the column nullable.
     */
    @Test
    void rejectsColumnWithExistingNull() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, NULL)");

        assertSqlState("23502", () -> setNotNull("note"));

        assertNullable(PUBLIC_SCHEMA, target(), "note");
    }

    /**
     * Sets NOT NULL on a populated table that already carries a validated
     * CHECK (note IS NOT NULL). This asserts the resulting state; it does not
     * observe whether PostgreSQL skipped its own scan.
     */
    @Test
    void setsNotNullWithValidCheckConstraint() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD CONSTRAINT note_nn CHECK (note IS NOT NULL) NOT VALID");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target() + " VALIDATE CONSTRAINT note_nn");

        setNotNull("note");

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
    }

    /**
     * Rejects null arguments through their domains before the body runs.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> setNotNull(null));
        assertDomainViolation(() -> setNotNull("note", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> setNotNull("note", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> setNotNull("note", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    private void setNotNull(String column) {
        setNotNull(column, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void setNotNull(String column, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.setNotNull(dsl.configuration(), PUBLIC_SCHEMA, target(), column,
                lockTimeout, sleepTime, duration);
    }
}

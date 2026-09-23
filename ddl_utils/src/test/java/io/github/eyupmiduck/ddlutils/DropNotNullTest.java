package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@code ddl_utils_lib.drop_not_null}: it builds an
 * {@code ALTER COLUMN ... DROP NOT NULL} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class DropNotNullTest extends SingleTableTest {

    DropNotNullTest() {
        super("drop_not_null_target", "id int NOT NULL, note text NOT NULL");
    }

    /**
     * Drops NOT NULL, making the column nullable without touching the others.
     */
    @Test
    void dropsNotNull() {
        dropNotNull("note");

        assertNullable(PUBLIC_SCHEMA, target(), "note");
        assertNotNullable(PUBLIC_SCHEMA, target(), "id");
    }

    /**
     * A previously rejected null value is accepted after dropping NOT NULL.
     */
    @Test
    void acceptsNullAfterDroppingNotNull() {
        dropNotNull("note");

        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, NULL)");
        Integer nulls = dsl.fetchOne(
                        "SELECT count(*)::int FROM " + PUBLIC_SCHEMA + "." + target() + " WHERE note IS NULL")
                .get(0, Integer.class);
        assertEquals(1, nulls);
    }

    /**
     * Rejects null arguments through their domains before the body runs.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> dropNotNull(null));
        assertDomainViolation(() -> dropNotNull("note", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> dropNotNull("note", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> dropNotNull("note", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    private void dropNotNull(String column) {
        dropNotNull(column, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void dropNotNull(String column, Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.dropNotNull(dsl.configuration(), PUBLIC_SCHEMA, target(), column,
                lockTimeout, sleepTime, duration);
    }
}

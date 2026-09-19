package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@code ddl_utils_lib.drop_not_null}: it builds an
 * {@code ALTER COLUMN ... DROP NOT NULL} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class DropNotNullTest extends PostgresTestBase {

    private static final String TARGET = "drop_not_null_target";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int NOT NULL, note text NOT NULL");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Drops NOT NULL, making the column nullable without touching the others.
     */
    @Test
    void dropsNotNull() {
        dropNotNull("note");

        assertEquals("YES", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "id", "is_nullable"));
    }

    /**
     * A previously rejected null value is accepted after dropping NOT NULL.
     */
    @Test
    void acceptsNullAfterDroppingNotNull() {
        dropNotNull("note");

        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, NULL)");
        Integer nulls = dsl.fetchOne(
                        "SELECT count(*)::int FROM " + PUBLIC_SCHEMA + "." + TARGET + " WHERE note IS NULL")
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
        Routines.dropNotNull(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column,
                lockTimeout, sleepTime, duration);
    }
}

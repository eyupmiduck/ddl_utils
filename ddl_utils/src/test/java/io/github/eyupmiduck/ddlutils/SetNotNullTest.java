package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@code ddl_utils_lib.set_not_null}: it builds an
 * {@code ALTER COLUMN ... SET NOT NULL} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class SetNotNullTest extends PostgresTestBase {

    private static final String TARGET = "set_not_null_target";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, note text");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Sets NOT NULL on a nullable column without touching the others.
     */
    @Test
    void setsNotNull() {
        setNotNull("note");

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals("YES", columnAttribute(PUBLIC_SCHEMA, TARGET, "id", "is_nullable"));
    }

    /**
     * Setting NOT NULL on a column that is already NOT NULL is a no-op, not an
     * error.
     */
    @Test
    void setsNotNullWhenAlreadySet() {
        setNotNull("note");
        setNotNull("note");

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
    }

    /**
     * A null value already present makes the call fail with a not-null
     * violation, leaving the column nullable.
     */
    @Test
    void rejectsColumnWithExistingNull() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, NULL)");

        assertSqlState("23502", () -> setNotNull("note"));

        assertEquals("YES", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
    }

    /**
     * A valid CHECK constraint proving the column non-null lets PostgreSQL skip
     * the scan, so the call succeeds on a populated table.
     */
    @Test
    void usesValidCheckConstraintToSkipScan() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, 'a')");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT note_nn CHECK (note IS NOT NULL) NOT VALID");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET + " VALIDATE CONSTRAINT note_nn");

        setNotNull("note");

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
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
        Routines.setNotNull(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column,
                lockTimeout, sleepTime, duration);
    }
}

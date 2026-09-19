package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the metadata-only column-attribute helpers
 * {@code ddl_utils_lib.set_column_storage} and {@code set_column_compression}:
 * each builds a validated fragment and applies it through
 * {@code ddl_utils_lib.alter_table}.
 */
class SetColumnAttributeTest extends PostgresTestBase {

    private static final String TARGET = "set_column_attribute_target";
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
     * Sets the storage mode, which is stored on the column.
     */
    @Test
    void setsStorage() {
        setStorage("note", "EXTERNAL");

        assertEquals("external", columnStorage(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * The storage keyword is case-insensitive, like the compression method.
     */
    @Test
    void acceptsCaseInsensitiveStorage() {
        setStorage("note", "external");

        assertEquals("external", columnStorage(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * An unknown storage mode is rejected.
     */
    @Test
    void rejectsUnknownStorage() {
        assertSqlState("22023", () -> setStorage("note", "COMPRESSED"));
    }

    /**
     * Sets the compression method, which is stored on the column.
     */
    @Test
    void setsCompression() {
        setCompression("note", "lz4");

        assertEquals("lz4", columnCompression(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * The compression method is case-insensitive.
     */
    @Test
    void acceptsCaseInsensitiveCompression() {
        setCompression("note", "PGLZ");

        assertEquals("pglz", columnCompression(PUBLIC_SCHEMA, TARGET, "note"));
    }

    /**
     * An unknown compression method is rejected.
     */
    @Test
    void rejectsUnknownCompression() {
        assertSqlState("22023", () -> setCompression("note", "zip"));
    }

    /**
     * Rejects null arguments through their domains before the body runs.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> setStorage("note", null));
        assertDomainViolation(() -> setStorage("note", "EXTERNAL", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> setStorage("note", "EXTERNAL", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> setStorage("note", "EXTERNAL", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
        assertDomainViolation(() -> setCompression("note", null));
        assertDomainViolation(() -> setCompression("note", "pglz", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> setCompression("note", "pglz", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> setCompression("note", "pglz", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    private void setStorage(String column, String storage) {
        setStorage(column, storage, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void setStorage(String column, String storage, Integer lockTimeout,
                            Integer sleepTime, Integer duration) {
        Routines.setColumnStorage(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column, storage,
                lockTimeout, sleepTime, duration);
    }

    private void setCompression(String column, String compression) {
        setCompression(column, compression, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void setCompression(String column, String compression, Integer lockTimeout,
                                Integer sleepTime, Integer duration) {
        Routines.setColumnCompression(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column, compression,
                lockTimeout, sleepTime, duration);
    }
}

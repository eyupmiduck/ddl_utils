package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the metadata-only column-attribute helpers
 * {@code ddl_utils_lib.set_column_statistics}, {@code set_column_storage} and
 * {@code set_column_compression}: each builds a validated fragment and applies
 * it through {@code ddl_utils_lib.alter_table}.
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
     * Sets a statistics target, which is stored on the column.
     */
    @Test
    void setsStatistics() {
        setStatistics("note", 500);

        assertEquals(500, statistics("note"));
    }

    /**
     * A target of -1 resets the column to the default: PostgreSQL 17 and later
     * store that as a null attstattarget, while PostgreSQL 16 stores -1, which
     * also means "use the default".
     */
    @Test
    void resetsStatistics() {
        setStatistics("note", 500);
        setStatistics("note", -1);

        Integer target = statistics("note");
        assertTrue(target == null || target == -1,
                () -> "expected the default statistics target; got: " + target);
    }

    /**
     * A statistics target below -1 or above 10000 is rejected.
     */
    @Test
    void rejectsOutOfRangeStatistics() {
        assertSqlState("22023", () -> setStatistics("note", -2));
        assertSqlState("22023", () -> setStatistics("note", 10001));
    }

    /**
     * A null statistics target is rejected rather than being emitted as
     * {@code SET STATISTICS NULL}.
     */
    @Test
    void rejectsNullStatistics() {
        assertSqlState("22023", () -> setStatistics("note", null));
    }

    /**
     * Sets the storage mode, which is stored on the column.
     */
    @Test
    void setsStorage() {
        setStorage("note", "EXTERNAL");

        assertEquals("external", storage("note"));
    }

    /**
     * The storage keyword is case-insensitive, like the compression method.
     */
    @Test
    void acceptsCaseInsensitiveStorage() {
        setStorage("note", "external");

        assertEquals("external", storage("note"));
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

        assertEquals("lz4", compression("note"));
    }

    /**
     * The compression method is case-insensitive.
     */
    @Test
    void acceptsCaseInsensitiveCompression() {
        setCompression("note", "PGLZ");

        assertEquals("pglz", compression("note"));
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
        assertDomainViolation(() -> setStatistics(null, 100));
        assertDomainViolation(() -> setStatistics("note", 100, null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> setStatistics("note", 100, DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> setStatistics("note", 100, DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
        assertDomainViolation(() -> setStorage("note", null));
        assertDomainViolation(() -> setStorage("note", "EXTERNAL", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> setStorage("note", "EXTERNAL", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> setStorage("note", "EXTERNAL", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
        assertDomainViolation(() -> setCompression("note", null));
        assertDomainViolation(() -> setCompression("note", "pglz", null, SLEEP_TIME, STATEMENT_DURATION));
        assertDomainViolation(() -> setCompression("note", "pglz", DDL_LOCK_TIMEOUT, null, STATEMENT_DURATION));
        assertDomainViolation(() -> setCompression("note", "pglz", DDL_LOCK_TIMEOUT, SLEEP_TIME, null));
    }

    private Integer statistics(String column) {
        return dsl.fetchOne(
                """
                        SELECT a.attstattarget
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, Integer.class);
    }

    private String storage(String column) {
        // attstorage is a single code: p=plain, e=external, m=main, x=extended.
        String code = dsl.fetchOne(
                """
                        SELECT a.attstorage::text
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, String.class);
        return switch (code) {
            case "p" -> "plain";
            case "e" -> "external";
            case "m" -> "main";
            case "x" -> "extended";
            default -> code;
        };
    }

    private String compression(String column) {
        // attcompression is a single code: p=pglz, l=lz4, empty=default.
        String code = dsl.fetchOne(
                """
                        SELECT a.attcompression::text
                        FROM pg_attribute a
                        JOIN pg_class c ON c.oid = a.attrelid
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ? AND a.attname = ?
                        """,
                PUBLIC_SCHEMA, TARGET, column).get(0, String.class);
        return switch (code) {
            case "p" -> "pglz";
            case "l" -> "lz4";
            case "" -> "default";
            default -> code;
        };
    }

    private void setStatistics(String column, Integer target) {
        setStatistics(column, target, DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void setStatistics(String column, Integer target, Integer lockTimeout,
                               Integer sleepTime, Integer duration) {
        Routines.setColumnStatistics(dsl.configuration(), PUBLIC_SCHEMA, TARGET, column, target,
                lockTimeout, sleepTime, duration);
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

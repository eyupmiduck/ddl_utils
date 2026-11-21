package io.github.eyupmiduck.ddlutils;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.assert_allowed_keyword}: it accepts a keyword
 * from the allow-list case-insensitively and rejects anything else.
 */
class AssertAllowedKeywordTest extends PostgresTestBase {

    /**
     * An allowed keyword is accepted regardless of case.
     */
    @Test
    void acceptsAllowedKeyword() {
        assertDoesNotThrow(() -> assertAllowedKeyword("ALWAYS", "ARRAY['always', 'by default']"));
        assertDoesNotThrow(() -> assertAllowedKeyword("by default", "ARRAY['always', 'by default']"));
        assertDoesNotThrow(() -> assertAllowedKeyword("LZ4", "ARRAY['pglz', 'lz4', 'default']"));
        // The allow-list entries are matched case-insensitively too.
        assertDoesNotThrow(() -> assertAllowedKeyword("always", "ARRAY['ALWAYS', 'By Default']"));
    }

    /**
     * A keyword outside the allow-list is rejected with an invalid-parameter
     * error. Near-miss values ensure the comparison is whole-token equality, not
     * a prefix/substring match: a trailing space survives non_null_text and must
     * not be trimmed away by the allow-list check.
     */
    @Test
    void rejectsDisallowedKeyword() {
        assertSqlState("22023",
                () -> assertAllowedKeyword("sometimes", "ARRAY['always', 'by default']"));
        assertSqlState("22023",
                () -> assertAllowedKeyword("alwaysx", "ARRAY['always', 'by default']"));
        assertSqlState("22023",
                () -> assertAllowedKeyword("alway", "ARRAY['always', 'by default']"));
        assertSqlState("22023",
                () -> assertAllowedKeyword("always ", "ARRAY['always', 'by default']"));
    }

    /**
     * The rejection message echoes the value and the allow-list, so the caller
     * can see the accepted keywords without reading the source.
     */
    @Test
    void messageEchoesTheAllowList() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> assertAllowedKeyword("sometimes", "ARRAY['always', 'by default']"));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("allowed: {always,\"by default\"}"),
                () -> "the message should echo the allow-list: " + failure.getMessage());
    }

    /**
     * A NULL, empty, or NULL-element allow-list cannot match, so the value is
     * rejected rather than silently accepted (the predicate must not be NULL).
     */
    @Test
    void rejectsNullOrEmptyAllowList() {
        assertSqlState("22023", () -> assertAllowedKeyword("always", "NULL::text[]"));
        assertSqlState("22023", () -> assertAllowedKeyword("always", "ARRAY[]::text[]"));
        assertSqlState("22023", () -> assertAllowedKeyword("always", "ARRAY[NULL]::text[]"));
    }

    private void assertAllowedKeyword(String value, String allowedExpression) {
        dsl.fetchOne(
                "SELECT ddl_utils_lib.assert_allowed_keyword(?, " + allowedExpression + ", ?)",
                value, "ctx");
    }
}

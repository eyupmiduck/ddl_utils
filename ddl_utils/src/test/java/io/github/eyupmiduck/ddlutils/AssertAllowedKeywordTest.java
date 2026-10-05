package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
     * error.
     */
    @Test
    void rejectsDisallowedKeyword() {
        assertSqlState("22023",
                () -> assertAllowedKeyword("sometimes", "ARRAY['always', 'by default']"));
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

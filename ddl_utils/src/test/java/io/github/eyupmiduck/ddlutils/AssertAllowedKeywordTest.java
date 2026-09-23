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

    private void assertAllowedKeyword(String value, String allowedExpression) {
        dsl.fetchOne(
                "SELECT ddl_utils_lib.assert_allowed_keyword(?, " + allowedExpression + ", ?)",
                value, "ctx");
    }
}

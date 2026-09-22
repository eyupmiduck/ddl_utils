package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies {@code ddl_utils_lib.assert_non_blank_elements}: it rejects blank
 * elements (using the same trim set as the {@code non_null_text} domain) while
 * allowing NULL elements, so a NULL default is not mistaken for a blank one.
 */
class AssertNonBlankElementsTest extends PostgresTestBase {

    /**
     * Non-blank elements are accepted.
     */
    @Test
    void acceptsNonBlankElements() {
        assertDoesNotThrow(() -> assertNonBlank("ARRAY['a', 'b']::ddl_utils.non_empty_text_array"));
    }

    /**
     * NULL elements are allowed (a NULL default means no DEFAULT clause).
     */
    @Test
    void acceptsNullElements() {
        assertDoesNotThrow(() -> assertNonBlank("ARRAY['a', NULL]::ddl_utils.non_empty_text_array"));
    }

    /**
     * A blank element is rejected with an invalid-parameter error.
     */
    @Test
    void rejectsBlankElement() {
        assertSqlState("22023", () -> assertNonBlank("ARRAY['a', '   ']::ddl_utils.non_empty_text_array"));
    }

    /**
     * Tabs, newlines and vertical tabs are blank. Vertical tab is the character
     * the buggy {@code E'\v'} literal was meant to represent (regression for the
     * trim set shared with the {@code non_null_text} domain).
     */
    @Test
    void rejectsNonSpaceWhitespace() {
        assertSqlState("22023", () -> assertNonBlank("ARRAY[E'\\t']::ddl_utils.non_empty_text_array"));
        assertSqlState("22023", () -> assertNonBlank("ARRAY[E'\\n']::ddl_utils.non_empty_text_array"));
        assertSqlState("22023", () -> assertNonBlank("ARRAY[E'\\013']::ddl_utils.non_empty_text_array"));
    }

    /**
     * A 0-based array is rejected, so element 0 is not silently skipped; the
     * helper enforces its 1-based precondition.
     */
    @Test
    void rejectsZeroBasedArray() {
        assertSqlState("22023",
                () -> assertNonBlank("'[0:1]={a,b}'::ddl_utils.non_empty_text_array"));
    }

    private void assertNonBlank(String valueExpression) {
        dsl.fetchOne(
                "SELECT ddl_utils_lib.assert_non_blank_elements(" + valueExpression + ", ?, ?)",
                "ctx", "label");
    }
}

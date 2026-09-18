package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.has_top_level_comma}: it detects a comma that
 * is not nested inside parentheses, brackets or a string literal, so callers
 * can reject SQL fragments that would append extra clauses.
 */
class HasTopLevelCommaTest extends PostgresTestBase {

    /**
     * A plain type with no comma is fine.
     */
    @Test
    void returnsFalseForPlainType() {
        assertFalse(hasTopLevelComma("int"));
    }

    /**
     * A comma separating two clauses is detected.
     */
    @Test
    void returnsTrueForTopLevelComma() {
        assertTrue(hasTopLevelComma("int, DROP COLUMN id"));
        assertTrue(hasTopLevelComma("0, ADD COLUMN backdoor text"));
    }

    /**
     * Commas inside parentheses or brackets are not top-level.
     */
    @Test
    void returnsFalseForCommasInsideGroups() {
        assertFalse(hasTopLevelComma("numeric(10,2)"));
        assertFalse(hasTopLevelComma("coalesce(1, 2)"));
        assertFalse(hasTopLevelComma("ARRAY[1,2]"));
        assertFalse(hasTopLevelComma("f(g(1,2), 3)"));
    }

    /**
     * A comma inside a string literal is data, not a separator.
     */
    @Test
    void returnsFalseForCommaInsideStringLiteral() {
        assertFalse(hasTopLevelComma("'a,b'"));
    }

    /**
     * A comma after a nested group is still top-level.
     */
    @Test
    void returnsTrueForCommaAfterGroup() {
        assertTrue(hasTopLevelComma("f(g(1,2)), x"));
    }

    /**
     * A null fragment has no comma.
     */
    @Test
    void returnsFalseForNull() {
        assertFalse(hasTopLevelComma(null));
    }

    private Boolean hasTopLevelComma(String value) {
        return dsl.fetchOne("SELECT ddl_utils_lib.has_top_level_comma(?)", (Object) value)
                .get(0, Boolean.class);
    }
}

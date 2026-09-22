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
        assertFalse(hasTopLevelComma("{a,b}"));
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

    /**
     * Commas inside comments, dollar-quoted literals and quoted identifiers are
     * opaque text, not separators.
     */
    @Test
    void returnsFalseForCommasInsideCommentsDollarQuotesAndIdentifiers() {
        assertFalse(hasTopLevelComma("$$a,b$$"));
        assertFalse(hasTopLevelComma("$q$a,b$q$"));
        assertFalse(hasTopLevelComma("\"a,b\""));
        assertFalse(hasTopLevelComma("coalesce(1, 2) /* a, b */"));
        assertFalse(hasTopLevelComma("1 -- a, b\n"));
    }

    /**
     * PostgreSQL block comments nest, so a comma inside an outer comment is not
     * top-level even when an inner comment has already closed. A bare CR also
     * ends a line comment.
     */
    @Test
    void handlesNestedBlockCommentsAndCarriageReturns() {
        assertFalse(hasTopLevelComma("/* a /* b */, x */"));
        assertTrue(hasTopLevelComma("/* a /* b */ c */, x"));
        assertTrue(hasTopLevelComma("1 -- a, b\r, x"));
    }

    /**
     * A comma after a closed comment, dollar quote or quoted identifier is
     * still top-level.
     */
    @Test
    void returnsTrueForCommaAfterCommentOrDollarQuote() {
        assertTrue(hasTopLevelComma("$$a$$, x"));
        assertTrue(hasTopLevelComma("1 /* c */, x"));
        assertTrue(hasTopLevelComma("1 -- c\n, x"));
        assertTrue(hasTopLevelComma("\"a\", x"));
    }

    /**
     * Escaped quotes inside string literals do not end the literal early.
     */
    @Test
    void handlesEscapedQuotes() {
        assertFalse(hasTopLevelComma("'a''b,c'"));
        assertFalse(hasTopLevelComma("E'a\\'b,c'"));
    }

    /**
     * An unbalanced group keeps its commas nested (conservative), while a
     * comma before the group is still top-level. A lone dollar sign (for
     * example a parameter) is not mistaken for a dollar quote.
     */
    @Test
    void treatsUnbalancedDelimitersConservatively() {
        assertFalse(hasTopLevelComma("f(a,b"));
        assertTrue(hasTopLevelComma("x, f(a,b"));
        assertTrue(hasTopLevelComma("$1, x"));
    }

    private Boolean hasTopLevelComma(String value) {
        return dsl.fetchOne("SELECT ddl_utils_lib.has_top_level_comma(?)", (Object) value)
                .get(0, Boolean.class);
    }
}

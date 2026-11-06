package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
     * In a standard literal a backslash is data, so the quote after it closes the
     * literal and the following comma is top-level. E-string escaping must not be
     * applied to standard literals.
     */
    @Test
    void treatsStandardLiteralBackslashAsData() {
        assertTrue(hasTopLevelComma("'a\\', x"));
        assertFalse(hasTopLevelComma("E'a\\', x'"));
    }

    /**
     * A stray closing delimiter at depth 0 is clamped, so the following comma is
     * still detected as top-level. Unterminated constructs are conservatively
     * treated as swallowing the rest of the value (the SQL is invalid anyway).
     */
    @Test
    void handlesUnbalancedDelimitersAndUnterminatedConstructs() {
        assertTrue(hasTopLevelComma("1), x"));
        assertTrue(hasTopLevelComma("1], x"));
        assertFalse(hasTopLevelComma("'a,b"));
        assertFalse(hasTopLevelComma("/* a,b"));
        assertFalse(hasTopLevelComma("$q$a,b"));
    }

    /**
     * A dollar tag only closes on the exact opening tag; a body containing a
     * shorter or longer overlapping tag does not end the literal early.
     */
    @Test
    void matchesTheExactDollarTag() {
        assertFalse(hasTopLevelComma("$tag$a,b$ta$, c$tag$"));
        assertFalse(hasTopLevelComma("$tag$a,b$tags$, c$tag$"));
        assertTrue(hasTopLevelComma("$tag$a,b$tag$, c"));
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

    private boolean hasTopLevelComma(String value) {
        Boolean result = dsl.fetchOne("SELECT ddl_utils_lib.has_top_level_comma(?)", (Object) value)
                .get(0, Boolean.class);
        assertNotNull(result, "has_top_level_comma must not return SQL NULL");
        return result;
    }
}

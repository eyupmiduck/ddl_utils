package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.has_unquoted_token}: it scans a SQL fragment
 * left to right, skipping strings, quoted identifiers, dollar-quoted literals
 * and comments, and reports a requested token only in code state (optionally
 * only at bracket depth 0).
 */
class HasUnquotedTokenTest extends PostgresTestBase {

    private static final String[] DDL_TOKENS = {";", "--", "/*", "*/"};

    /**
     * A statement separator or comment marker in code is reported.
     */
    @Test
    void returnsTrueForStatementTokens() {
        assertTrue(hasUnquotedToken("ADD COLUMN x int; DROP TABLE victim", DDL_TOKENS, false));
        assertTrue(hasUnquotedToken("ADD COLUMN x int -- trailing", DDL_TOKENS, false));
        assertTrue(hasUnquotedToken("ADD COLUMN x int /* comment */", DDL_TOKENS, false));
        assertTrue(hasUnquotedToken("ADD COLUMN x int */", DDL_TOKENS, false));
    }

    /**
     * Separators and comment markers inside quoted text are data, not tokens.
     */
    @Test
    void returnsFalseForQuotedText() {
        assertFalse(hasUnquotedToken("ADD COLUMN x text DEFAULT 'a;b'", DDL_TOKENS, false));
        assertFalse(hasUnquotedToken("ADD COLUMN \"x--y\" int", DDL_TOKENS, false));
        assertFalse(hasUnquotedToken("ADD COLUMN \"a'b\" int", DDL_TOKENS, false));
        assertFalse(hasUnquotedToken("ADD COLUMN x text DEFAULT $$a;b$$", DDL_TOKENS, false));
        assertFalse(hasUnquotedToken("ADD COLUMN x text DEFAULT $tag$--not a comment$tag$", DDL_TOKENS, false));
        assertFalse(hasUnquotedToken("ADD COLUMN x text DEFAULT E'it\\'s;ok'", DDL_TOKENS, false));
    }

    /**
     * The segment-stacking bypass that motivated the scanner is detected: a
     * dollar-quoted value hides the apostrophe from a naive scrub, but the
     * semicolon after the closing tag is in code state.
     */
    @Test
    void detectsDollarQuoteStatementStacking() {
        assertTrue(hasUnquotedToken(
                "ALTER COLUMN note SET DEFAULT $q$'$q$ ; DROP TABLE victim; SELECT 'x'",
                DDL_TOKENS, false));
    }

    /**
     * The top-level flag limits a token to bracket depth 0 while still skipping
     * it (rather than treating it as a separator) inside a nested group.
     */
    @Test
    void respectsTopLevelOnly() {
        assertFalse(hasUnquotedToken("f(a,b)", new String[]{","}, true));
        assertTrue(hasUnquotedToken("f(a,b)", new String[]{","}, false));
        assertFalse(hasUnquotedToken("f(a;b)", new String[]{";"}, true));
        assertTrue(hasUnquotedToken("f(a;b)", new String[]{";"}, false));
    }

    /**
     * A NULL value contains no token.
     */
    @Test
    void returnsFalseForNull() {
        assertFalse(hasUnquotedToken(null, DDL_TOKENS, false));
    }

    /**
     * A blank token is rejected rather than matching every position.
     */
    @Test
    void rejectsBlankToken() {
        assertSqlState("22023", () -> hasUnquotedToken("ADD COLUMN x int", new String[]{" "}, false));
    }

    /**
     * Any SQL whitespace makes a token blank, not just a space.
     */
    @Test
    void rejectsWhitespaceOnlyTokens() {
        for (String token : new String[]{"\t", "\n", "\r", "\f", "\u000B", " \t\n"}) {
            assertSqlState("22023", () -> hasUnquotedToken("ADD COLUMN x int", new String[]{token}, false));
        }
    }

    /**
     * A dollar-quote tag may contain non-ASCII letters, so a semicolon inside
     * the literal is data rather than a token.
     */
    @Test
    void returnsFalseForNonAsciiDollarQuoteTag() {
        assertFalse(hasUnquotedToken("ADD COLUMN x text DEFAULT $café$;$café$", DDL_TOKENS, false));
        assertFalse(hasUnquotedToken("ADD COLUMN x text DEFAULT $é$;$é$", DDL_TOKENS, false));
    }

    /**
     * A dollar sign and a non-ASCII letter continue an identifier, so the e/E
     * before a quote is not an E-string prefix: PostgreSQL sees an identifier
     * followed by a string and the backslash does not escape the closing quote.
     */
    @Test
    void doesNotTreatIdentifierSuffixAsEStringPrefix() {
        assertTrue(hasUnquotedToken(
                "ADD COLUMN x int DEFAULT foo$e'\\'; DROP TABLE victim", DDL_TOKENS, false));
        assertTrue(hasUnquotedToken(
                "ADD COLUMN x int DEFAULT xée'\\'; DROP TABLE victim", DDL_TOKENS, false));
    }

    /**
     * Block comments nest, so a token inside the outer comment is skipped.
     */
    @Test
    void returnsFalseForNestedBlockComment() {
        assertFalse(hasUnquotedToken("ADD COLUMN x int /* /* ; */ */", new String[]{";"}, false));
    }

    /**
     * A bare carriage return ends a line comment, so a following token is in
     * code state.
     */
    @Test
    void detectsTokenAfterBareCarriageReturn() {
        assertTrue(hasUnquotedToken("ADD COLUMN x int -- a\r; DROP TABLE victim", DDL_TOKENS, false));
    }

    /**
     * A line comment running to the end of the value is valid and reports no
     * token; it is not an unterminated construct.
     */
    @Test
    void acceptsLineCommentAtEndOfValue() {
        assertFalse(hasUnquotedToken("ADD COLUMN x int -- trailing", new String[]{";"}, false));
    }

    /**
     * Malformed input is classified conservatively: a stray closer keeps the
     * current depth, and an unterminated construct swallows the rest of the
     * value. The SQL is invalid and fails when executed, so no token is reported.
     */
    @Test
    void classifiesMalformedInputConservatively() {
        assertTrue(hasUnquotedToken("f(a) ; )", new String[]{";"}, false));
        assertFalse(hasUnquotedToken("f(a ; )", new String[]{";"}, true));
        assertFalse(hasUnquotedToken("a ';", new String[]{";"}, false));
        assertFalse(hasUnquotedToken("a /* ;", new String[]{";"}, false));
        assertFalse(hasUnquotedToken("a $q$;", new String[]{";"}, false));
    }

    private Boolean hasUnquotedToken(String value, String[] tokens, boolean topLevelOnly) {
        return dsl.fetchOne("SELECT ddl_utils_lib.has_unquoted_token(?, ?, ?)",
                        (Object) value, tokens, topLevelOnly)
                .get(0, Boolean.class);
    }
}

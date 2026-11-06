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

    private Boolean hasUnquotedToken(String value, String[] tokens, boolean topLevelOnly) {
        return dsl.fetchOne("SELECT ddl_utils_lib.has_unquoted_token(?, ?, ?)",
                        (Object) value, tokens, topLevelOnly)
                .get(0, Boolean.class);
    }
}

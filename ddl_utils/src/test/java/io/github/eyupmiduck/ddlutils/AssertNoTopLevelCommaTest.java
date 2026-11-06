package io.github.eyupmiduck.ddlutils;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.assert_no_top_level_comma}: it raises when a
 * value contains a top-level comma (which could append an ALTER TABLE action)
 * and accepts commas nested in groups or string literals.
 */
class AssertNoTopLevelCommaTest extends PostgresTestBase {

    /**
     * A comma-free value is accepted.
     */
    @Test
    void acceptsCommaFreeValue() {
        assertDoesNotThrow(() -> assertNoTopLevelComma("int"));
    }

    /**
     * A NULL value means "no DEFAULT clause" and is accepted; an empty string
     * has no top-level comma either.
     */
    @Test
    void acceptsNullAndEmptyValue() {
        assertDoesNotThrow(() -> assertNoTopLevelComma(null));
        assertDoesNotThrow(() -> assertNoTopLevelComma(""));
    }

    /**
     * A comma nested in parentheses or a string literal is accepted.
     */
    @Test
    void acceptsNestedComma() {
        assertDoesNotThrow(() -> assertNoTopLevelComma("numeric(10,2)"));
        assertDoesNotThrow(() -> assertNoTopLevelComma("'a,b'"));
    }

    /**
     * A top-level comma is rejected with an invalid-parameter error.
     */
    @Test
    void rejectsTopLevelComma() {
        assertSqlState("22023", () -> assertNoTopLevelComma("0, ADD COLUMN backdoor text"));
    }

    /**
     * Representative top-level comma positions are all rejected: leading,
     * trailing, doubled, whitespace-separated, and before a different ALTER
     * action.
     */
    @Test
    void rejectsRepresentativeTopLevelCommaForms() {
        for (String value : new String[]{
                ", ADD COLUMN backdoor text",
                "0,",
                "0,, 1",
                "0 , ADD COLUMN backdoor text",
                "0, DROP COLUMN id",
                "0,\tADD COLUMN backdoor text",
                "  0, x"}) {
            assertSqlState("22023", () -> assertNoTopLevelComma(value));
        }
    }

    /**
     * A guard that enters a nested group or quoted literal must return to code
     * state before a later comma; otherwise it would accept the exact injection
     * it exists to reject.
     */
    @Test
    void rejectsNestedCommaFollowedByTopLevelComma() {
        assertSqlState("22023", () -> assertNoTopLevelComma(
                "numeric(10,2), ADD COLUMN backdoor text"));
        assertSqlState("22023", () -> assertNoTopLevelComma(
                "coalesce('a,b', 0), ADD COLUMN backdoor text"));
        assertSqlState("22023", () -> assertNoTopLevelComma(
                "'a''b,c', x"));
    }

    /**
     * The rejection message carries the caller's context, so a failure can be
     * traced to the specific expression being validated.
     */
    @Test
    void includesContextInErrorMessage() {
        String context = "distinctive-context-42";
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> assertNoTopLevelComma("0, ADD COLUMN backdoor text", context));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains(context),
                () -> "message should contain the context: " + failure.getMessage());
        assertTrue(failure.getMessage().toLowerCase().contains("comma"),
                () -> "message should name the top-level comma: " + failure.getMessage());
    }

    private void assertNoTopLevelComma(String value) {
        assertNoTopLevelComma(value, "ctx");
    }

    private void assertNoTopLevelComma(String value, String context) {
        dsl.fetchOne("SELECT ddl_utils_lib.assert_no_top_level_comma(?, ?)", value, context);
    }
}

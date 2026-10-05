package io.github.eyupmiduck.ddlutils;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "  0, x"}) {
            assertSqlState("22023", () -> assertNoTopLevelComma(value));
        }
    }

    /**
     * The rejection message carries the caller's context, so a failure can be
     * traced to the specific expression being validated.
     */
    @Test
    void includesContextInErrorMessage() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> assertNoTopLevelComma("0, ADD COLUMN backdoor text"));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("ctx"),
                () -> "message should contain the context: " + failure.getMessage());
        assertTrue(failure.getMessage().toLowerCase().contains("comma"),
                () -> "message should name the top-level comma: " + failure.getMessage());
    }

    private void assertNoTopLevelComma(String value) {
        dsl.fetchOne("SELECT ddl_utils_lib.assert_no_top_level_comma(?, ?)", value, "ctx");
    }
}

package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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

    private void assertNoTopLevelComma(String value) {
        dsl.fetchOne("SELECT ddl_utils_lib.assert_no_top_level_comma(?, ?)", value, "ctx");
    }
}

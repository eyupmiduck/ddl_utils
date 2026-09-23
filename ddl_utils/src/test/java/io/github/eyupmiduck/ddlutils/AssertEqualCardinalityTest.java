package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies {@code ddl_utils_lib.assert_equal_cardinality}: it accepts arrays of
 * equal length and rejects a mismatch, including a NULL array.
 */
class AssertEqualCardinalityTest extends PostgresTestBase {

    /**
     * Arrays of equal length are accepted.
     */
    @Test
    void acceptsEqualLengths() {
        assertDoesNotThrow(() -> assertEqualCardinality("ARRAY['a', 'b']", "ARRAY['c', 'd']"));
    }

    /**
     * Arrays of different length are rejected with an invalid-parameter error.
     */
    @Test
    void rejectsDifferentLengths() {
        assertSqlState("22023", () -> assertEqualCardinality("ARRAY['a', 'b']", "ARRAY['c']"));
    }

    /**
     * A NULL array is rejected: its cardinality is NULL, so it is not equal to
     * the other array's length.
     */
    @Test
    void rejectsNullArray() {
        assertSqlState("22023", () -> assertEqualCardinality("NULL::text[]", "ARRAY['c']"));
    }

    private void assertEqualCardinality(String a, String b) {
        dsl.fetchOne(
                "SELECT ddl_utils_lib.assert_equal_cardinality(" + a + "::text[], " + b
                        + "::text[], ?)", "ctx");
    }
}

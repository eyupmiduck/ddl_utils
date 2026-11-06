package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies {@code ddl_utils_lib.assert_equal_cardinality}: it accepts arrays of
 * equal cardinality, including two empty arrays, and rejects a mismatch, a
 * single NULL array and two NULL arrays.
 */
class AssertEqualCardinalityTest extends PostgresTestBase {

    /**
     * Arrays of equal length are accepted.
     */
    @Test
    void acceptsEqualLengths() {
        assertDoesNotThrow(() -> assertEqualCardinality(
                "ARRAY['a', 'b']::text[]", "ARRAY['c', 'd']::text[]"));
    }

    /**
     * Arrays of different length are rejected with an invalid-parameter error.
     */
    @Test
    void rejectsDifferentLengths() {
        assertSqlState("22023", () -> assertEqualCardinality(
                "ARRAY['a', 'b']::text[]", "ARRAY['c']::text[]"));
    }

    /**
     * A NULL array is rejected: its cardinality is NULL, so it is not equal to
     * the other array's length.
     */
    @Test
    void rejectsNullArray() {
        assertSqlState("22023", () -> assertEqualCardinality(
                "NULL::text[]", "ARRAY['c']::text[]"));
    }

    /**
     * Two NULL arrays are rejected: both cardinalities are NULL, so
     * {@code IS DISTINCT FROM} alone would treat them as equal.
     */
    @Test
    void rejectsBothNullArrays() {
        assertSqlState("22023", () -> assertEqualCardinality("NULL::text[]", "NULL::text[]"));
    }

    /**
     * Two empty arrays have the same cardinality (0) and are accepted.
     */
    @Test
    void acceptsTwoEmptyArrays() {
        assertDoesNotThrow(() -> assertEqualCardinality("ARRAY[]::text[]", "ARRAY[]::text[]"));
    }

    /**
     * An empty array and a NULL array do not have equal cardinality.
     */
    @Test
    void rejectsEmptyAgainstNull() {
        assertSqlState("22023", () -> assertEqualCardinality("ARRAY[]::text[]", "NULL::text[]"));
    }

    /**
     * Cardinality is the total element count, so equal-size multidimensional
     * arrays are accepted.
     */
    @Test
    void acceptsEqualCardinalityMultidimensionalArrays() {
        assertDoesNotThrow(() -> assertEqualCardinality(
                "ARRAY[['a', 'b']]::text[]", "ARRAY[['c', 'd']]::text[]"));
    }

    private void assertEqualCardinality(String a, String b) {
        // Both arguments are compile-time SQL literals supplied by the test.
        dsl.fetchOne("SELECT ddl_utils_lib.assert_equal_cardinality(" + a + ", " + b + ", ?)", "ctx");
    }
}

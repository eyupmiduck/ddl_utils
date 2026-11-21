package io.github.eyupmiduck.ddlutils;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.assert_one_based}: it rejects a NULL, empty,
 * multidimensional or non-1-based array, so helpers that loop from subscript 1
 * cannot read NULL out of range. The argument is polymorphic, so any element
 * type is accepted.
 */
class AssertOneBasedTest extends PostgresTestBase {

    /**
     * A 1-based text array is accepted.
     */
    @Test
    void acceptsOneBasedTextArray() {
        assertDoesNotThrow(() -> assertOneBased("'{a,b}'::text[]"));
    }

    /**
     * A 1-based array of another element type is accepted.
     */
    @Test
    void acceptsOneBasedBooleanArray() {
        assertDoesNotThrow(() -> assertOneBased("'{true,false}'::boolean[]"));
    }

    /**
     * A 0-based array is rejected with an invalid-parameter error.
     */
    @Test
    void rejectsZeroBasedArray() {
        assertSqlState("22023", () -> assertOneBased("'[0:1]={a,b}'::text[]"));
    }

    /**
     * A 2-based array is rejected too; the check pins the lower bound to
     * exactly 1 rather than merely requiring a non-negative bound.
     */
    @Test
    void rejectsTwoBasedArray() {
        assertSqlState("22023", () -> assertOneBased("'[2:3]={a,b}'::text[]"));
    }

    /**
     * An empty array has no lower bound and is rejected with its own message.
     */
    @Test
    void rejectsEmptyArray() {
        assertSqlState("22023", () -> assertOneBased("'{}'::text[]"));
    }

    /**
     * A NULL array is rejected: {@code array_lower} is NULL, so a plain
     * {@code <> 1} test would skip the check entirely.
     */
    @Test
    void rejectsNullArray() {
        assertSqlState("22023", () -> assertOneBased("NULL::text[]"));
    }

    /**
     * A multidimensional array is rejected: helpers index a single subscript,
     * so they cannot address such an array's elements.
     */
    @Test
    void rejectsMultidimensionalArray() {
        assertSqlState("22023", () -> assertOneBased("ARRAY[['a'], ['b']]::text[]"));
    }

    /**
     * A non-empty multidimensional array reports the dimensionality defect, not
     * the lower-bound check.
     */
    @Test
    void multidimensionalArrayReportsItsShape() {
        assertMessageContains("ARRAY[['a'], ['b']]::text[]", "array must be one-dimensional");
    }

    /**
     * An empty array literal written with an inner empty array is still just an
     * empty array: PostgreSQL collapses a zero-length dimension, so
     * {@code array_ndims} is NULL and the value is indistinguishable from
     * {@code '{}'}. It therefore reports emptiness; there is no observable
     * empty-but-multidimensional array to report a shape for.
     */
    @Test
    void emptyMultidimensionalLiteralCollapsesToEmpty() {
        assertMessageContains("ARRAY[ARRAY[]::text[]]", "array must not be empty");
    }

    /**
     * An empty one-dimensional array still reports that it is empty:
     * {@code array_ndims} is NULL for it, so the dimensionality check must not
     * swallow this case.
     */
    @Test
    void emptyOneDimensionalArrayReportsEmptiness() {
        assertMessageContains("'{}'::text[]", "array must not be empty");
    }

    /**
     * A 0-based array reports the lower-bound defect.
     */
    @Test
    void zeroBasedArrayReportsTheLowerBound() {
        assertMessageContains("'[0:1]={a,b}'::text[]", "array must be 1-based");
    }

    /**
     * Asserts that the call fails with 22023 and the expected message fragment.
     */
    private void assertMessageContains(String valueExpression, String expected) {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> assertOneBased(valueExpression));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains(expected),
                () -> "expected \"" + expected + "\" in: " + failure.getMessage());
    }

    private void assertOneBased(String valueExpression) {
        dsl.fetchOne("SELECT ddl_utils_lib.assert_one_based(" + valueExpression + ", ?)", "ctx");
    }
}

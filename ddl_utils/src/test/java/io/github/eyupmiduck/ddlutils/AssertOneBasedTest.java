package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies {@code ddl_utils_lib.assert_one_based}: it rejects an array whose
 * lower bound is not 1, so helpers that loop from subscript 1 cannot read NULL
 * out of range. The argument is polymorphic, so any element type is accepted.
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
     * A NULL array is rejected: {@code array_lower} is NULL, so a plain
     * {@code <> 1} test would skip the check entirely.
     */
    @Test
    void rejectsNullArray() {
        assertSqlState("22023", () -> assertOneBased("NULL::text[]"));
    }

    private void assertOneBased(String valueExpression) {
        dsl.fetchOne("SELECT ddl_utils_lib.assert_one_based(" + valueExpression + ", ?)", "ctx");
    }
}

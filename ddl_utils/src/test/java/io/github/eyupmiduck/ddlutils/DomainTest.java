package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that the Liquibase changelog creates the
 * {@code ddl_utils.non_negative_integer} and {@code ddl_utils.non_null_text}
 * domains with the expected constraints.
 */
class DomainTest extends PostgresTestBase {

    /**
     * The integer domain accepts zero and positive values.
     */
    @Test
    void nonNegativeIntegerAcceptsZeroAndPositiveValues() {
        assertEquals(0, evaluate("0::ddl_utils.non_negative_integer"));
        assertEquals(7, evaluate("7::ddl_utils.non_negative_integer"));
    }

    /**
     * The integer domain rejects negative values.
     */
    @Test
    void nonNegativeIntegerRejectsNegativeValues() {
        assertThrows(DataAccessException.class,
                () -> evaluate("(-1)::ddl_utils.non_negative_integer"));
    }

    /**
     * The integer domain rejects null.
     */
    @Test
    void nonNegativeIntegerRejectsNull() {
        assertThrows(DataAccessException.class,
                () -> evaluate("NULL::ddl_utils.non_negative_integer"));
    }

    /**
     * The text domain accepts a non-null value.
     */
    @Test
    void nonNullTextAcceptsText() {
        assertEquals("hello", evaluate("'hello'::ddl_utils.non_null_text"));
    }

    /**
     * The text domain rejects null.
     */
    @Test
    void nonNullTextRejectsNull() {
        assertThrows(DataAccessException.class,
                () -> evaluate("NULL::ddl_utils.non_null_text"));
    }

    private Object evaluate(String expression) {
        Record record = dsl.fetchOne("SELECT " + expression);
        assertNotNull(record, () -> "Query returned no row: " + expression);
        return record.get(0);
    }
}

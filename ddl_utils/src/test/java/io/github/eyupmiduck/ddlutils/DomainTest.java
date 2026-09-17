package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Liquibase changelog creates the
 * {@code ddl_utils.non_negative_integer}, {@code ddl_utils.non_null_text}, and
 * {@code ddl_utils.non_null_boolean} domains with the expected constraints.
 */
class DomainTest extends PostgresTestBase {

    /**
     * The integer domain accepts zero and positive values.
     */
    @Test
    void nonNegativeIntegerAcceptsZeroAndPositiveValues() {
        assertEquals(0, evaluate("0::ddl_utils.non_negative_integer", Integer.class));
        assertEquals(7, evaluate("7::ddl_utils.non_negative_integer", Integer.class));
    }

    /**
     * The integer domain rejects negative values with a check-constraint
     * violation.
     */
    @Test
    void nonNegativeIntegerRejectsNegativeValues() {
        assertDomainViolation(() -> evaluate("(-1)::ddl_utils.non_negative_integer", Integer.class));
    }

    /**
     * The integer domain rejects null with a check-constraint violation.
     */
    @Test
    void nonNegativeIntegerRejectsNull() {
        assertDomainViolation(() -> evaluate("NULL::ddl_utils.non_negative_integer", Integer.class));
    }

    /**
     * The text domain accepts a non-null value.
     */
    @Test
    void nonNullTextAcceptsText() {
        assertEquals("hello", evaluate("'hello'::ddl_utils.non_null_text", String.class));
    }

    /**
     * The text domain rejects null with a check-constraint violation.
     */
    @Test
    void nonNullTextRejectsNull() {
        assertDomainViolation(() -> evaluate("NULL::ddl_utils.non_null_text", String.class));
    }

    /**
     * The text domain rejects an empty string with a check-constraint
     * violation.
     */
    @Test
    void nonNullTextRejectsEmptyString() {
        assertDomainViolation(() -> evaluate("''::ddl_utils.non_null_text", String.class));
    }

    /**
     * The text domain rejects a whitespace-only string with a
     * check-constraint violation.
     */
    @Test
    void nonNullTextRejectsBlankString() {
        assertDomainViolation(() -> evaluate("'   '::ddl_utils.non_null_text", String.class));
    }

    /**
     * The boolean domain accepts true and false.
     */
    @Test
    void nonNullBooleanAcceptsTrueAndFalse() {
        assertEquals(true, evaluate("true::ddl_utils.non_null_boolean", Boolean.class));
        assertEquals(false, evaluate("false::ddl_utils.non_null_boolean", Boolean.class));
    }

    /**
     * The boolean domain rejects null with a check-constraint violation.
     */
    @Test
    void nonNullBooleanRejectsNull() {
        assertDomainViolation(() -> evaluate("NULL::ddl_utils.non_null_boolean", Boolean.class));
    }

    private <T> T evaluate(String expression, Class<T> type) {
        Record record = dsl.fetchOne("SELECT " + expression);
        assertNotNull(record, () -> "Query returned no row: " + expression);
        return record.get(0, type);
    }
}

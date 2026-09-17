package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Liquibase changelog creates the
 * {@code ddl_utils.non_negative_integer}, {@code ddl_utils.non_null_text}, and
 * {@code ddl_utils.non_null_boolean} domains with the expected constraints.
 */
class DomainTest extends PostgresTestBase {

    /**
     * Asserts that the query fails with SQLSTATE {@code 23514}
     * ({@code check_violation}), proving the domain constraint exists rather
     * than the domain merely being absent.
     */
    private static void assertCheckViolation(Executable query) {
        DataAccessException exception = assertThrows(DataAccessException.class, query);
        assertEquals("23514", sqlState(exception),
                () -> "expected a check-constraint violation but was: " + exception.getMessage());
    }

    private static String sqlState(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }

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
        assertCheckViolation(() -> evaluate("(-1)::ddl_utils.non_negative_integer", Integer.class));
    }

    /**
     * The integer domain rejects null with a check-constraint violation.
     */
    @Test
    void nonNegativeIntegerRejectsNull() {
        assertCheckViolation(() -> evaluate("NULL::ddl_utils.non_negative_integer", Integer.class));
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
        assertCheckViolation(() -> evaluate("NULL::ddl_utils.non_null_text", String.class));
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
        assertCheckViolation(() -> evaluate("NULL::ddl_utils.non_null_boolean", Boolean.class));
    }

    private <T> T evaluate(String expression, Class<T> type) {
        Record record = dsl.fetchOne("SELECT " + expression);
        assertNotNull(record, () -> "Query returned no row: " + expression);
        return record.get(0, type);
    }
}

package io.github.eyupmiduck.ddlutils;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils_lib.assert_identifier_length}: it rejects an
 * identifier longer than PostgreSQL's {@code NAMEDATALEN - 1} (63) bytes,
 * measures multibyte names in bytes rather than characters, names the offending
 * argument, and ignores a NULL value.
 */
class AssertIdentifierLengthTest extends PostgresTestBase {

    /**
     * Exactly 63 bytes is legal and accepted.
     */
    @Test
    void acceptsSixtyThreeBytes() {
        assertDoesNotThrow(() -> assertIdentifierLength("a".repeat(63), "ctx", "i_name"));
    }

    /**
     * A 63-byte value measured in bytes may contain multibyte characters, so
     * the check must not count characters.
     */
    @Test
    void acceptsSixtyThreeBytesOfMultibyteCharacters() {
        // 31 two-byte characters plus one ASCII byte.
        assertDoesNotThrow(() -> assertIdentifierLength("\u00E9".repeat(31) + "a", "ctx", "i_name"));
    }

    /**
     * A NULL value is ignored by the helper: callers that reject NULL do so in
     * their own check (a domain or an explicit test).
     */
    @Test
    void acceptsNullValue() {
        assertDoesNotThrow(() -> dsl.fetchOne(
                "SELECT ddl_utils_lib.assert_identifier_length(NULL::text, ?, ?)", "ctx", "i_name"));
    }

    /**
     * A 64-byte value is rejected with 22023, and the message names the
     * argument and the byte length so a caller with several identifiers can
     * tell which one is over-long.
     */
    @Test
    void rejectsSixtyFourBytesNamingTheArgument() {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> assertIdentifierLength("a".repeat(64), "ddl_utils_lib.example", "i_column_name"));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("ddl_utils_lib.example"),
                () -> "the message should carry the caller context: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("i_column_name must be at most 63 bytes (64 bytes)"),
                () -> "the message should name the argument and length: " + failure.getMessage());
    }

    /**
     * A multibyte value is measured in bytes: 32 two-byte characters are 64
     * bytes and are rejected even though the character count is 32.
     */
    @Test
    void rejectsMultibyteValueOverTheByteLimit() {
        assertSqlState("22023",
                () -> assertIdentifierLength("\u00E9".repeat(32), "ctx", "i_name"));
    }

    private void assertIdentifierLength(String value, String context, String argument) {
        dsl.fetchOne("SELECT ddl_utils_lib.assert_identifier_length(?, ?, ?)", value, context, argument);
    }
}

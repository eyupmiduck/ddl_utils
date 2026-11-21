package io.github.eyupmiduck.ddlutils;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.quote_identifiers}: it joins identifiers into a
 * comma-separated list, quoting each with {@code %I} and applying an optional
 * clause prefix to every element.
 */
class QuoteIdentifiersTest extends PostgresTestBase {

    /**
     * Identifiers are joined in order, separated by a comma and a space.
     */
    @Test
    void joinsIdentifiersInOrder() {
        assertEquals("b, a, c",
                quoteIdentifiers("ARRAY['b', 'a', 'c']::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * An identifier that needs quoting is quoted, so a spaced name survives.
     */
    @Test
    void quotesIdentifiersThatNeedIt() {
        assertEquals("\"weird name\"",
                quoteIdentifiers("ARRAY['weird name']::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * The prefix is applied to every element, so a repeated clause keyword such
     * as {@code DROP COLUMN} is emitted once per identifier.
     */
    @Test
    void appliesPrefixToEveryElement() {
        assertEquals("DROP COLUMN a, DROP COLUMN b",
                quoteIdentifiers("ARRAY['a', 'b']::ddl_utils.non_empty_non_null_text_array",
                        "DROP COLUMN "));
    }

    /**
     * A blank element would become an empty identifier {@code ""}, which is not
     * a valid PostgreSQL identifier; it is rejected.
     */
    @Test
    void rejectsBlankElement() {
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY['a', '  ']::ddl_utils.non_empty_non_null_text_array"));
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY[E'\\t\\n']::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * An element longer than NAMEDATALEN - 1 (63) bytes would be emitted in full
     * by {@code %I} but silently truncated by PostgreSQL, so it is rejected.
     */
    @Test
    void rejectsOverlongElement() {
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY[repeat('a', 64)]::ddl_utils.non_empty_non_null_text_array"));
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY[repeat('a', 63), repeat('b', 64)]::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * Exactly NAMEDATALEN - 1 bytes is legal and is not rejected by the
     * {@code > 63} boundary check.
     */
    @Test
    void acceptsExactly63Bytes() {
        String value = "a".repeat(63);

        assertEquals(value, quoteIdentifiers(
                "ARRAY[repeat('a', 63)]::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * The limit is measured in bytes, not characters: 32 two-byte characters
     * are 64 bytes and would be truncated by PostgreSQL.
     */
    @Test
    void rejectsMultibyteOverlongElement() {
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY[repeat('\u00e9', 32)]::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * The prefix and the per-element quoting compose correctly (a prefix +
     * quoted identifier, not a quoted prefix or an unquoted name).
     */
    @Test
    void appliesPrefixAndQuotes() {
        assertEquals("DROP COLUMN \"Mixed Name\"",
                quoteIdentifiers("ARRAY['Mixed Name']::ddl_utils.non_empty_non_null_text_array",
                        "DROP COLUMN "));
    }

    /**
     * The SQL default for i_prefix (an empty string) is usable; this is the
     * path {@code add_foreign_key} relies on when it passes only the identifier
     * arrays (the other callers pass an explicit prefix).
     */
    @Test
    void appliesDefaultEmptyPrefix() {
        assertEquals("a, b", quoteIdentifiersDefault(
                "ARRAY['a', 'b']::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * An actually empty identifier is rejected even though the array domain
     * only requires one non-NULL element.
     */
    @Test
    void rejectsEmptyIdentifier() {
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY['']::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * All characters in the shared trim set are blank, including a carriage
     * return and a form feed.
     */
    @Test
    void rejectsAllTrimCharacters() {
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY[E'\\r']::ddl_utils.non_empty_non_null_text_array"));
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY[E'\\f']::ddl_utils.non_empty_non_null_text_array"));
        assertSqlState("22023", () -> quoteIdentifiers(
                "ARRAY[E'\\013']::ddl_utils.non_empty_non_null_text_array"));
    }

    /**
     * A NULL prefix is rejected by the sql_prefix domain: format() is strict, so
     * the function would otherwise return NULL instead of an identifier list.
     */
    @Test
    void rejectsNullPrefixThroughTheDomain() {
        assertSqlState("23502", () -> quoteIdentifiers(
                "ARRAY['a']::ddl_utils.non_empty_non_null_text_array", null));
    }

    /**
     * The over-long error names the first offending identifier and its byte
     * length, so a long column list is diagnosable.
     */
    @Test
    void overlongErrorNamesTheOffenderAndLength() {
        DataAccessException failure = assertThrows(DataAccessException.class, () -> quoteIdentifiers(
                "ARRAY[repeat('a', 63), repeat('b', 64)]::ddl_utils.non_empty_non_null_text_array"));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("b".repeat(64)),
                () -> "the message should name the offending identifier: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("(64 bytes)"),
                () -> "the message should state the byte length: " + failure.getMessage());
    }

    /**
     * The blank error names the first offending element.
     */
    @Test
    void blankErrorNamesTheOffender() {
        DataAccessException failure = assertThrows(DataAccessException.class, () -> quoteIdentifiers(
                "ARRAY['a', E'\\t ']::ddl_utils.non_empty_non_null_text_array"));

        assertEquals("22023", failure.sqlState());
        assertTrue(failure.getMessage().contains("<\t >"),
                () -> "the message should name the offending element: " + failure.getMessage());
    }

    private String quoteIdentifiers(String valueExpression) {
        return quoteIdentifiers(valueExpression, "");
    }

    private String quoteIdentifiersDefault(String valueExpression) {
        // valueExpression is a compile-time literal supplied by the test.
        return dsl.fetchOne(
                        "SELECT ddl_utils_lib.quote_identifiers(" + valueExpression + ")")
                .get(0, String.class);
    }

    private String quoteIdentifiers(String valueExpression, String prefix) {
        // valueExpression is a compile-time literal supplied by the test.
        return dsl.fetchOne(
                        "SELECT ddl_utils_lib.quote_identifiers(" + valueExpression + ", ?)", prefix)
                .get(0, String.class);
    }
}

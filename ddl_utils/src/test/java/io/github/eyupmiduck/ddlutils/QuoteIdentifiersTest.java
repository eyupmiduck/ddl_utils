package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private String quoteIdentifiers(String valueExpression) {
        return quoteIdentifiers(valueExpression, "");
    }

    private String quoteIdentifiers(String valueExpression, String prefix) {
        return dsl.fetchOne(
                        "SELECT ddl_utils_lib.quote_identifiers(" + valueExpression + ", ?)", prefix)
                .get(0, String.class);
    }
}

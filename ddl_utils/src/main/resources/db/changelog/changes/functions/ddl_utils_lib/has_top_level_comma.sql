CREATE OR REPLACE FUNCTION ddl_utils_lib.has_top_level_comma(
    i_value text
)
    RETURNS boolean
    LANGUAGE plpgsql
    IMMUTABLE
    SECURITY INVOKER
AS
$$
DECLARE
    l_scrubbed text;
    l_next     text;
BEGIN
    IF i_value IS NULL THEN
        RETURN false;
    END IF;

    -- Commas inside string literals are data, not separators; drop them.
    l_scrubbed := pg_catalog.regexp_replace(i_value, $re$'([^']|'')*'$re$, '', 'g');

    -- Remove parenthesised/bracketed groups innermost-first until the nesting
    -- is gone, leaving only the top-level structure.
    LOOP
        l_next := pg_catalog.regexp_replace(
                l_scrubbed, $re$\([^()]*\)|\[[^\[\]]*\]|\{[^{}]*\}$re$, '', 'g');
        EXIT WHEN l_next = l_scrubbed;
        l_scrubbed := l_next;
    END LOOP;

    RETURN pg_catalog.strpos(l_scrubbed, ',') > 0;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.has_top_level_comma IS
    'Returns whether a value contains a comma outside parentheses, brackets or strings.';

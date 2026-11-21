CREATE OR REPLACE FUNCTION ddl_utils_lib.has_top_level_comma(
    i_value text
)
    RETURNS boolean
    LANGUAGE plpgsql
    IMMUTABLE
    SECURITY INVOKER
AS
$func$
BEGIN
    -- The documented contract: a NULL value has no comma. Check it here so the
    -- contract does not rely on has_unquoted_token's own NULL handling.
    IF i_value IS NULL THEN
        RETURN false;
    END IF;

    -- The token scanner is shared with the alter_table fragment guard.
    RETURN ddl_utils_lib.has_unquoted_token(
            i_value => i_value,
            i_tokens => ARRAY [','],
            i_top_level_only => true
           );
END;
$func$;

COMMENT ON FUNCTION ddl_utils_lib.has_top_level_comma IS
    'Returns whether a value contains a comma outside parentheses, brackets, '
        'braces, string/dollar-quoted literals, quoted identifiers or comments. '
        'A NULL value returns false.';

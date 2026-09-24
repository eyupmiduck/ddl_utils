CREATE OR REPLACE FUNCTION ddl_utils_lib.quote_identifiers(
    i_values ddl_utils.non_empty_non_null_text_array,
    i_prefix text DEFAULT ''
)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    SECURITY INVOKER
AS
$$
    -- i_prefix is a literal SQL clause prefix (for example 'DROP COLUMN '), not a
-- value; each identifier is still quoted with %I. i_prefix is text rather than
-- ddl_utils.non_null_text because an empty prefix is valid.
SELECT pg_catalog.string_agg(
               pg_catalog.format('%s%I', i_prefix, t.value),
               ', ' ORDER BY t.ordinality)
FROM pg_catalog.unnest(i_values) WITH ORDINALITY AS t(value, ordinality);
$$;

COMMENT ON FUNCTION ddl_utils_lib.quote_identifiers IS
    'Joins an array of identifiers into a comma-separated list, each quoted '
        'with %I and optionally prefixed.';

CREATE OR REPLACE FUNCTION ddl_utils_lib.assert_allowed_keyword(
    i_value ddl_utils.non_null_text,
    i_allowed text[],
    i_context ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    IMMUTABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- i_allowed must be lower-case; the comparison is case-insensitive, so a
    -- caller may pass ALWAYS or always. The message does not echo i_allowed:
    -- array_to_string is STABLE, which would stop this function being IMMUTABLE.
    IF NOT (pg_catalog.lower(i_value) = ANY (i_allowed)) THEN
        RAISE EXCEPTION '%: % is not an allowed keyword', i_context, i_value
            USING ERRCODE = '22023';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.assert_allowed_keyword IS
    'Raises 22023 when a value is not one of a lower-case keyword allow-list.';

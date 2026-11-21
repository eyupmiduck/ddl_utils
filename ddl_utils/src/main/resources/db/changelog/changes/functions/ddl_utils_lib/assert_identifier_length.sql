CREATE OR REPLACE FUNCTION ddl_utils_lib.assert_identifier_length(
    i_value text,
    i_context ddl_utils.non_null_text,
    i_argument ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- PostgreSQL truncates an identifier longer than NAMEDATALEN - 1 (63) bytes
    -- with only a NOTICE, so a routine that emits the value with %I would
    -- appear to succeed while the object is stored under the truncated name.
    -- Subsequent references by the requested name then fail, or resolve to a
    -- different object. i_value is text rather than non_null_text so a caller
    -- can pass a value whose NULL is rejected by another check (the explicit
    -- NULL test below keeps that case out of octet_length).
    IF i_value IS NOT NULL AND pg_catalog.octet_length(i_value) > 63 THEN
        RAISE EXCEPTION '%: % must be at most 63 bytes (% bytes)',
            i_context, i_argument, pg_catalog.octet_length(i_value)
            USING ERRCODE = '22023';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.assert_identifier_length IS
    'Raises 22023 when an identifier is longer than NAMEDATALEN - 1 (63) bytes, '
        'naming the argument; a NULL value is ignored.';

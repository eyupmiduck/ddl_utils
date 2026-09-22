CREATE OR REPLACE FUNCTION ddl_utils_lib.assert_one_based(
    i_values anyarray,
    i_context ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    IMMUTABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- The array domains constrain cardinality but not the lower bound, so a
    -- caller could pass '[0:1]={a,b}'. Helpers that loop from subscript 1 would
    -- then read NULL out of range; reject any array that does not start at 1.
    -- IS DISTINCT FROM also rejects a NULL array, for which array_lower is NULL.
    IF pg_catalog.array_lower(i_values, 1) IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION '%: array must be 1-based', i_context
            USING ERRCODE = '22023';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.assert_one_based IS
    'Raises 22023 when an array does not start at subscript 1.';

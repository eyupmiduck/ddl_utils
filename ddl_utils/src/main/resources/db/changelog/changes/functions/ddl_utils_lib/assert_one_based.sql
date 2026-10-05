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
    -- Reject a multidimensional array too, for the same reason: a helper that
    -- indexes one subscript cannot address its elements. IS DISTINCT FROM also
    -- rejects a NULL array, for which both functions return NULL.
    IF pg_catalog.array_ndims(i_values) IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION '%: array must be one-dimensional', i_context
            USING ERRCODE = '22023';
    ELSIF pg_catalog.array_lower(i_values, 1) IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION '%: array must be 1-based', i_context
            USING ERRCODE = '22023';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.assert_one_based IS
    'Raises 22023 when an array is not one-dimensional or does not start at subscript 1.';

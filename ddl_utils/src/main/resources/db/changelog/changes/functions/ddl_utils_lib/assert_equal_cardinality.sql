CREATE OR REPLACE FUNCTION ddl_utils_lib.assert_equal_cardinality(
    i_a anyarray,
    i_b anyarray,
    i_context ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    IMMUTABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- IS DISTINCT FROM also rejects a NULL array, whose cardinality is NULL.
    IF pg_catalog.cardinality(i_a) IS DISTINCT FROM pg_catalog.cardinality(i_b) THEN
        RAISE EXCEPTION '%: arrays must have the same length (% vs %)',
            i_context, pg_catalog.cardinality(i_a), pg_catalog.cardinality(i_b)
            USING ERRCODE = '22023';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.assert_equal_cardinality IS
    'Raises 22023 when two arrays do not have the same number of elements.';

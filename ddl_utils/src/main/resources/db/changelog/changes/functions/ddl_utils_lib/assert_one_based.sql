CREATE OR REPLACE FUNCTION ddl_utils_lib.assert_one_based(
    i_values anyarray,
    i_context ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
BEGIN
    -- The array domains constrain cardinality but not the lower bound, so a
    -- caller could pass '[0:1]={a,b}'. Helpers that loop from subscript 1 would
    -- then read NULL out of range; reject any array that does not start at 1.
    -- Reject NULL, empty and multidimensional arrays too, for the same reason.
    -- The function raises, so it is STABLE rather than IMMUTABLE: a constant
    -- call must not be folded (and raise) at plan time for a branch that would
    -- never run.
    IF i_values IS NULL THEN
        RAISE EXCEPTION '%: array must not be NULL', i_context
            USING ERRCODE = '22023';
    ELSIF pg_catalog.array_ndims(i_values) > 1 THEN
        -- Check the dimensionality before the cardinality so a genuinely
        -- multidimensional value reports its shape rather than some other
        -- defect. A `> 1` test (rather than `IS DISTINCT FROM 1`) is deliberate:
        -- PostgreSQL collapses an array with a zero-length dimension to an empty
        -- array, for which array_ndims is NULL. Such a value is indistinguishable
        -- from '{}' and must still report that it is empty below, not that it is
        -- "not one-dimensional".
        RAISE EXCEPTION '%: array must be one-dimensional', i_context
            USING ERRCODE = '22023';
    ELSIF pg_catalog.cardinality(i_values) = 0 THEN
        RAISE EXCEPTION '%: array must not be empty', i_context
            USING ERRCODE = '22023';
    ELSIF pg_catalog.array_lower(i_values, 1) IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION '%: array must be 1-based', i_context
            USING ERRCODE = '22023';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.assert_one_based IS
    'Raises 22023 when an array is NULL, empty, not one-dimensional, or does '
        'not start at subscript 1.';

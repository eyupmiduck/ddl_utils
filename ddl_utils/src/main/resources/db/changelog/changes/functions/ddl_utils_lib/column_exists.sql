CREATE OR REPLACE FUNCTION ddl_utils_lib.column_exists(
    i_relation regclass,
    i_column_name ddl_utils.non_null_text
)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY INVOKER
AS
$$
    SELECT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_attribute AS a
        WHERE a.attrelid = i_relation
          AND a.attname = i_column_name
          AND a.attnum > 0
          AND NOT a.attisdropped
        );
$$;

COMMENT ON FUNCTION ddl_utils_lib.column_exists(regclass, ddl_utils.non_null_text) IS
    'True when the relation has a live, non-dropped column with this name; a '
        'missing column returns false rather than raising.';

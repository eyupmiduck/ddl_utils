CREATE OR REPLACE FUNCTION ddl_utils.clear_table_lock_settings(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS
$$
BEGIN
    DELETE
    FROM ddl_utils.table_lock_settings
    WHERE schema_name = i_schema_name
      AND table_name = i_table_name;
END;
$$;

COMMENT ON FUNCTION ddl_utils.clear_table_lock_settings IS
    'Deletes the lock settings for a table; a no-op when there are none.';

CREATE OR REPLACE FUNCTION ddl_utils.clear_schema_lock_settings(
    i_schema_name ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS
$$
BEGIN
    -- SECURITY DEFINER: the caller may clear any schema's settings. That trust
    -- is intentional; ddl_utils_caller is the application role.
    DELETE
    FROM ddl_utils.schema_lock_settings
    WHERE schema_name = i_schema_name;
END;
$$;

COMMENT ON FUNCTION ddl_utils.clear_schema_lock_settings IS
    'Deletes the lock settings for a schema; a no-op when there are none.';

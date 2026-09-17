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
    DELETE FROM ddl_utils.schema_lock_settings
    WHERE schema_name = i_schema_name;
END;
$$;

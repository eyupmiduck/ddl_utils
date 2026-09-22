CREATE OR REPLACE FUNCTION ddl_utils.rename_table(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_new_table_name ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_settings ddl_utils.lock_settings;
BEGIN
    -- Lock-aware wrapper: resolves the settings for the table and delegates to
    -- the generic helper.
    SELECT *
    INTO l_settings
    FROM ddl_utils.get_lock_settings(
                 i_schema_name => i_schema_name,
                 i_table_name => i_table_name
         ) AS ls;

    PERFORM ddl_utils_lib.rename_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_new_table_name => i_new_table_name,
            i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
            i_sleep_time => l_settings.sleep_time,
            i_statement_duration => l_settings.statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils.rename_table IS
    'Lock-aware wrapper: renames a table.';

CREATE OR REPLACE FUNCTION ddl_utils.drop_constraint_if_exists(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_settings ddl_utils.lock_settings;
BEGIN
    l_settings := ddl_utils_lib.table_lock_settings(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name);

    PERFORM ddl_utils_lib.drop_constraint_if_exists(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_constraint_name => i_constraint_name,
            i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
            i_sleep_time => l_settings.sleep_time,
            i_statement_duration => l_settings.statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils.drop_constraint_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) IS
    'Lock-aware wrapper: drops a same-named constraint of any kind when it '
        'exists and is a no-op otherwise.';

CREATE OR REPLACE FUNCTION ddl_utils.add_primary_key_using_index_if_absent(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_index_name ddl_utils.non_null_text
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

    PERFORM ddl_utils_lib.add_primary_key_using_index_if_absent(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_constraint_name => i_constraint_name,
            i_index_name => i_index_name,
            i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
            i_sleep_time => l_settings.sleep_time,
            i_statement_duration => l_settings.statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils.add_primary_key_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) IS
    'Lock-aware wrapper: attaches a pre-built unique index as the primary key '
        'when the constraint is absent, and is a no-op when it already matches.';

CREATE OR REPLACE FUNCTION ddl_utils.add_columns(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
    i_column_types ddl_utils.non_empty_non_null_text_array,
    i_default_values ddl_utils.non_empty_text_array,
    i_nullable ddl_utils.non_empty_non_null_boolean_array
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_ddl_lock_timeout   integer;
    l_sleep_time         integer;
    l_statement_duration integer;
BEGIN
    SELECT ls.ddl_lock_timeout, ls.sleep_time, ls.statement_duration
    INTO l_ddl_lock_timeout, l_sleep_time, l_statement_duration
    FROM ddl_utils.get_lock_settings(i_schema_name, i_table_name) AS ls;

    PERFORM ddl_utils_lib.add_columns(
        i_schema_name => i_schema_name,
        i_table_name => i_table_name,
        i_column_names => i_column_names,
        i_column_types => i_column_types,
        i_default_values => i_default_values,
        i_nullable => i_nullable,
        i_ddl_lock_timeout => l_ddl_lock_timeout,
        i_sleep_time => l_sleep_time,
        i_statement_duration => l_statement_duration
    );
END;
$$;

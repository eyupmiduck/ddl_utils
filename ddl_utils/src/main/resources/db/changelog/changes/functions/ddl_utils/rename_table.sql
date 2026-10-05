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
    l_settings           ddl_utils.lock_settings;
    l_ddl_lock_timeout   integer;
    l_sleep_time         integer;
    l_statement_duration integer;
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

    -- Table-specific lock settings are keyed by the literal table name, so move
    -- any override to the new name; otherwise the renamed table silently falls
    -- back to the schema/database defaults and the old override is orphaned.
    -- set_/clear_table_lock_settings are SECURITY DEFINER because the caller
    -- has only SELECT on the settings table.
    SELECT tls.ddl_lock_timeout, tls.sleep_time, tls.statement_duration
    INTO l_ddl_lock_timeout, l_sleep_time, l_statement_duration
    FROM ddl_utils.table_lock_settings AS tls
    WHERE tls.schema_name = i_schema_name
      AND tls.table_name = i_table_name;

    IF FOUND THEN
        PERFORM ddl_utils.set_table_lock_settings(
                i_schema_name => i_schema_name,
                i_table_name => i_new_table_name,
                i_ddl_lock_timeout => l_ddl_lock_timeout,
                i_sleep_time => l_sleep_time,
                i_statement_duration => l_statement_duration
                );
        PERFORM ddl_utils.clear_table_lock_settings(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name
                );
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils.rename_table IS
    'Lock-aware wrapper: renames a table and moves its table lock settings to the '
        'new name.';

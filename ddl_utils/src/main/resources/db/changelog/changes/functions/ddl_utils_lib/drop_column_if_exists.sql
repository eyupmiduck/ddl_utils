CREATE OR REPLACE FUNCTION ddl_utils_lib.drop_column_if_exists(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_name ddl_utils.non_null_text,
    i_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_sleep_time ddl_utils.non_negative_integer,
    i_statement_duration ddl_utils.non_negative_integer
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_relation regclass;
BEGIN
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_column_name,
            i_context => 'ddl_utils_lib.drop_column_if_exists',
            i_argument => 'i_column_name');

    l_relation := ddl_utils_lib.resolve_table_relation(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_context => 'ddl_utils_lib.drop_column_if_exists',
            i_relation_label => 'table');

    -- Avoid the ACCESS EXCLUSIVE lock when the column is already absent. The
    -- ALTER still uses IF EXISTS so a concurrent external drop is a no-op.
    IF NOT ddl_utils_lib.column_exists(
            i_relation => l_relation,
            i_column_name => i_column_name) THEN
        RETURN;
    END IF;

    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format('DROP COLUMN IF EXISTS %I', i_column_name),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.drop_column_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) IS
    'Drops a column when it exists and is a no-op otherwise. No CASCADE: '
        'dependent objects still fail the ALTER. The ALTER uses DROP COLUMN '
        'IF EXISTS so a concurrent external drop is also a no-op.';

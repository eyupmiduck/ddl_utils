CREATE OR REPLACE FUNCTION ddl_utils.add_column(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_name ddl_utils.non_null_text,
    i_column_type ddl_utils.non_null_text,
    i_default_value text,
    i_nullable ddl_utils.non_null_boolean,
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
    l_fragment text;
BEGIN
    l_fragment := pg_catalog.format('ADD COLUMN %I %s', i_column_name, i_column_type);

    -- i_default_value is raw SQL so that expressions such as now() work; pass
    -- quoted literals (for example '''x''') when a literal is wanted. A NULL
    -- value means no DEFAULT clause at all. alter_table rejects fragments that
    -- contain a statement separator or comment.
    IF i_default_value IS NOT NULL THEN
        l_fragment := l_fragment || pg_catalog.format(' DEFAULT %s', i_default_value);
    END IF;

    IF NOT i_nullable THEN
        l_fragment := l_fragment || ' NOT NULL';
    END IF;

    PERFORM ddl_utils.alter_table(
        i_schema_name => i_schema_name,
        i_table_name => i_table_name,
        i_alter_table_fragment => l_fragment,
        i_ddl_lock_timeout => i_ddl_lock_timeout,
        i_sleep_time => i_sleep_time,
        i_statement_duration => i_statement_duration
    );
END;
$$;

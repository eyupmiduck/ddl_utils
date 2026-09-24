CREATE OR REPLACE FUNCTION ddl_utils_lib.add_identity(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_name ddl_utils.non_null_text,
    i_generated ddl_utils.non_null_text,
    i_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_sleep_time ddl_utils.non_negative_integer,
    i_statement_duration ddl_utils.non_negative_integer
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
BEGIN
    -- The generated mode is a two-value keyword set; validate it so a caller
    -- cannot splice arbitrary SQL. It is emitted verbatim (not as an
    -- identifier) because these are unquoted PostgreSQL keywords.
    PERFORM ddl_utils_lib.assert_allowed_keyword(
            i_value => i_generated,
            i_allowed => ARRAY ['always', 'by default'],
            i_context => 'ddl_utils_lib.add_identity: generated');

    -- Adding an identity is metadata-only (like SET DEFAULT) and affects future
    -- rows only. PostgreSQL requires the column to be NOT NULL (it raises 42P16
    -- otherwise), and the new sequence starts at 1, so adding it to a populated
    -- column can hand out duplicate values to later inserts.
    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format(
                    'ALTER COLUMN %I ADD GENERATED %s AS IDENTITY',
                    i_column_name,
                    upper(i_generated)
                                      ),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.add_identity IS
    'Adds an identity to a column, taking the lock settings explicitly. The '
        'column must already be NOT NULL; the sequence starts at 1.';

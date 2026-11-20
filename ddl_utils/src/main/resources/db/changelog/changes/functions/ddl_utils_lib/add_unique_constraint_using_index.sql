CREATE OR REPLACE FUNCTION ddl_utils_lib.add_unique_constraint_using_index(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_index_name ddl_utils.non_null_text,
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
    IF pg_catalog.octet_length(i_constraint_name) > 63 THEN
        RAISE EXCEPTION
            'ddl_utils_lib.add_unique_constraint_using_index: the constraint name must be at most 63 bytes'
            USING ERRCODE = '22023';
    END IF;

    PERFORM ddl_utils_lib.validate_unique_index_for_attach(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_index_name => i_index_name,
            i_context => 'ddl_utils_lib.add_unique_constraint_using_index');

    -- Attaches an already-built unique index as a unique constraint. Build the
    -- index with CREATE UNIQUE INDEX CONCURRENTLY first so the table is never
    -- held under ACCESS EXCLUSIVE for the build; the attach itself still takes
    -- a brief ACCESS EXCLUSIVE lock (hence the alter_table retry).
    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format(
                    'ADD CONSTRAINT %I UNIQUE USING INDEX %I',
                    i_constraint_name,
                    i_index_name
                                      ),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.add_unique_constraint_using_index IS
    'Attaches a pre-built unique index as a unique constraint. The index '
        'preconditions are validated before the ALTER.';

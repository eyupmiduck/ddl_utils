CREATE OR REPLACE FUNCTION ddl_utils_lib.drop_constraint(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
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
    -- Metadata-only. IF EXISTS is deliberately not used so a wrong name fails
    -- fast rather than silently doing nothing. RESTRICT (the default) makes
    -- dropping a primary key or unique constraint that a foreign key references
    -- fail with 2BP01; there is no CASCADE option.
    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format('DROP CONSTRAINT %I', i_constraint_name),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.drop_constraint IS
    'Drops a constraint, taking the lock settings explicitly. Emits RESTRICT '
        '(the default), so dropping a primary key or unique constraint that a '
        'foreign key references raises 2BP01; drop the dependent foreign keys '
        'first. There is no CASCADE option.';

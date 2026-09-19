CREATE OR REPLACE FUNCTION ddl_utils_lib.add_check_constraint(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_check_expression ddl_utils.non_null_text,
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
    -- The expression is arbitrary SQL, so reject only a top-level comma that
    -- could append another ALTER TABLE action. NOT VALID makes the constraint
    -- metadata-only (no scan); call validate_constraint separately to enforce
    -- it against existing rows.
    IF ddl_utils_lib.has_top_level_comma(i_check_expression) THEN
        RAISE EXCEPTION
            'ddl_utils_lib.add_check_constraint: the check expression contains a top-level comma'
            USING ERRCODE = '22023';
    END IF;

    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format(
                    'ADD CONSTRAINT %I CHECK (%s) NOT VALID',
                    i_constraint_name,
                    i_check_expression
                                      ),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

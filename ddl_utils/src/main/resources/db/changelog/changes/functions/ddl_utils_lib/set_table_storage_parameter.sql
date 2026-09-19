CREATE OR REPLACE FUNCTION ddl_utils_lib.set_table_storage_parameter(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_parameter_name ddl_utils.non_null_text,
    i_parameter_value ddl_utils.non_null_text,
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
    -- Restrict the parameters to the set PostgreSQL accepts under SHARE UPDATE
    -- EXCLUSIVE, so a rewrite-inducing option (such as a fillfactor-independent
    -- tablespace move or a type-changing option) cannot be reached. The name is
    -- emitted verbatim (not as an identifier); the value is a plain token.
    IF lower(i_parameter_name) NOT IN (
                                       'fillfactor',
                                       'autovacuum_enabled',
                                       'autovacuum_vacuum_threshold',
                                       'autovacuum_vacuum_scale_factor',
                                       'autovacuum_analyze_threshold',
                                       'autovacuum_analyze_scale_factor',
                                       'autovacuum_vacuum_cost_delay',
                                       'autovacuum_vacuum_cost_limit',
                                       'autovacuum_freeze_min_age',
                                       'autovacuum_freeze_max_age',
                                       'autovacuum_freeze_table_age',
                                       'autovacuum_multixact_freeze_min_age',
                                       'autovacuum_multixact_freeze_max_age',
                                       'autovacuum_multixact_freeze_table_age',
                                       'toast.autovacuum_enabled',
                                       'toast.autovacuum_vacuum_threshold',
                                       'toast.autovacuum_vacuum_scale_factor',
                                       'toast.autovacuum_analyze_threshold',
                                       'toast.autovacuum_analyze_scale_factor',
                                       'toast.autovacuum_vacuum_cost_delay',
                                       'toast.autovacuum_vacuum_cost_limit',
                                       'toast.autovacuum_freeze_min_age',
                                       'toast.autovacuum_freeze_max_age',
                                       'toast.autovacuum_freeze_table_age',
                                       'toast.autovacuum_multixact_freeze_min_age',
                                       'toast.autovacuum_multixact_freeze_max_age',
                                       'toast.autovacuum_multixact_freeze_table_age',
                                       'parallel_workers'
        ) THEN
        RAISE EXCEPTION
            'ddl_utils_lib.set_table_storage_parameter: unsupported storage parameter %',
            i_parameter_name
            USING ERRCODE = '22023';
    END IF;

    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format(
                    'SET (%s = %s)',
                    lower(i_parameter_name),
                    i_parameter_value
                                      ),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

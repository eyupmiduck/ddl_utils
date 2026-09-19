CREATE OR REPLACE FUNCTION ddl_utils_lib.set_column_statistics(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_name ddl_utils.non_null_text,
    i_statistics integer,
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
    -- Statistics targets run from 0 to 10000; -1 resets the column to the
    -- default. This is metadata-only and takes only SHARE UPDATE EXCLUSIVE, so
    -- it does not block concurrent DML. NULL is rejected explicitly: the range
    -- test alone would evaluate to NULL and let `SET STATISTICS NULL` through.
    IF i_statistics IS NULL
        OR (i_statistics <> -1 AND (i_statistics < 0 OR i_statistics > 10000)) THEN
        RAISE EXCEPTION
            'ddl_utils_lib.set_column_statistics: the statistics target must be -1 or between 0 and 10000'
            USING ERRCODE = '22023';
    END IF;

    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format(
                    'ALTER COLUMN %I SET STATISTICS %s',
                    i_column_name,
                    i_statistics
                                      ),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

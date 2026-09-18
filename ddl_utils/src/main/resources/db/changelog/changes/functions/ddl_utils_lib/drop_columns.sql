CREATE OR REPLACE FUNCTION ddl_utils_lib.drop_columns(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
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
    l_fragment text    := '';
    l_count    integer := pg_catalog.cardinality(i_column_names);
BEGIN
    FOR l_index IN 1..l_count
        LOOP
        -- The array domain allows blank elements; reject them here so a
        -- blank name cannot produce an empty identifier. The trim set must
        -- stay in step with the ddl_utils.non_null_text domain
        -- (004-create-domains.sql), which scalar names are checked against.
            IF pg_catalog.btrim(i_column_names[l_index], E' \t\n\r\f\v') = '' THEN
                RAISE EXCEPTION 'ddl_utils_lib.drop_columns: column name at position % is blank', l_index
                    USING ERRCODE = '22023';
            END IF;

            IF l_index > 1 THEN
                l_fragment := l_fragment || ', ';
            END IF;

            -- The column names are identifiers, so they are quoted with %I and
            -- the fragment contains no caller-supplied SQL.
            l_fragment := l_fragment || pg_catalog.format('DROP COLUMN %I', i_column_names[l_index]);
        END LOOP;

    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => l_fragment,
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

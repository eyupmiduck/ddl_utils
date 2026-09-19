CREATE OR REPLACE FUNCTION ddl_utils_lib.add_foreign_key(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
    i_referenced_schema_name ddl_utils.non_null_text,
    i_referenced_table_name ddl_utils.non_null_text,
    i_referenced_column_names ddl_utils.non_empty_non_null_text_array,
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
    l_columns            text := '';
    l_referenced_columns text := '';
    l_count              integer;
BEGIN
    l_count := pg_catalog.cardinality(i_column_names);
    IF pg_catalog.cardinality(i_referenced_column_names) <> l_count THEN
        RAISE EXCEPTION
            'ddl_utils_lib.add_foreign_key: the referencing and referenced column lists must have the same length (columns=%, referenced=%)',
            l_count,
            pg_catalog.cardinality(i_referenced_column_names)
            USING ERRCODE = '22023';
    END IF;

    FOR l_index IN 1..l_count
        LOOP
        -- The array domains allow blank elements; reject them here. The trim
        -- set must stay in step with the ddl_utils.non_null_text domain
        -- (004-create-domains.sql).
            IF pg_catalog.btrim(i_column_names[l_index], E' \t\n\r\f\v') = '' THEN
                RAISE EXCEPTION 'ddl_utils_lib.add_foreign_key: column name at position % is blank', l_index
                    USING ERRCODE = '22023';
            END IF;
            IF pg_catalog.btrim(i_referenced_column_names[l_index], E' \t\n\r\f\v') = '' THEN
                RAISE EXCEPTION 'ddl_utils_lib.add_foreign_key: referenced column name at position % is blank', l_index
                    USING ERRCODE = '22023';
            END IF;

            IF l_index > 1 THEN
                l_columns := l_columns || ', ';
                l_referenced_columns := l_referenced_columns || ', ';
            END IF;

            l_columns := l_columns || pg_catalog.format('%I', i_column_names[l_index]);
            l_referenced_columns := l_referenced_columns
                || pg_catalog.format('%I', i_referenced_column_names[l_index]);
        END LOOP;

    -- NOT VALID makes the constraint metadata-only (no scan of either table);
    -- call validate_constraint separately to enforce it. Referencing a foreign
    -- key takes SHARE ROW EXCLUSIVE on both tables, not ACCESS EXCLUSIVE.
    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format(
                    'ADD CONSTRAINT %I FOREIGN KEY (%s) REFERENCES %I.%I (%s) NOT VALID',
                    i_constraint_name,
                    l_columns,
                    i_referenced_schema_name,
                    i_referenced_table_name,
                    l_referenced_columns
                                      ),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

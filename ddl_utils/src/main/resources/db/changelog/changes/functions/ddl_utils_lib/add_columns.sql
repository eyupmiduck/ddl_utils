CREATE OR REPLACE FUNCTION ddl_utils_lib.add_columns(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
    i_column_types ddl_utils.non_empty_non_null_text_array,
    i_default_values ddl_utils.non_empty_text_array,
    i_nullable ddl_utils.non_empty_non_null_boolean_array,
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
    l_fragment text := '';
    l_index    integer;
    l_count    integer := pg_catalog.cardinality(i_column_names);
BEGIN
    IF pg_catalog.cardinality(i_column_types) <> l_count
        OR pg_catalog.cardinality(i_default_values) <> l_count
        OR pg_catalog.cardinality(i_nullable) <> l_count THEN
        RAISE EXCEPTION
            'ddl_utils_lib.add_columns: column arrays must have the same length (columns=%, types=%, defaults=%, nullable=%)',
            l_count,
            pg_catalog.cardinality(i_column_types),
            pg_catalog.cardinality(i_default_values),
            pg_catalog.cardinality(i_nullable)
            USING ERRCODE = '22023';
    END IF;

    FOR l_index IN 1..l_count LOOP
        -- The array domains allow blank elements; reject them here so a blank
        -- name or type cannot produce an empty identifier or malformed SQL.
        IF pg_catalog.btrim(i_column_names[l_index]) = '' THEN
            RAISE EXCEPTION 'ddl_utils_lib.add_columns: column name at position % is blank', l_index
                USING ERRCODE = '22023';
        END IF;
        IF pg_catalog.btrim(i_column_types[l_index]) = '' THEN
            RAISE EXCEPTION 'ddl_utils_lib.add_columns: the type for column % is blank',
                i_column_names[l_index]
                USING ERRCODE = '22023';
        END IF;

        -- The type and default are spliced into the statement as raw SQL, so a
        -- top-level comma could terminate the clause and append more DDL (for
        -- example a type of 'int, DROP COLUMN x').
        IF ddl_utils_lib.has_top_level_comma(i_column_types[l_index])
            OR ddl_utils_lib.has_top_level_comma(i_default_values[l_index]) THEN
            RAISE EXCEPTION
                'ddl_utils_lib.add_columns: the column type or default for column % contains a top-level comma',
                i_column_names[l_index]
                USING ERRCODE = '22023';
        END IF;

        IF l_index > 1 THEN
            l_fragment := l_fragment || ', ';
        END IF;

        l_fragment := l_fragment || pg_catalog.format(
            'ADD COLUMN %I %s',
            i_column_names[l_index],
            i_column_types[l_index]
        );

        -- A NULL default means no DEFAULT clause; a non-null default is raw SQL
        -- (for example now()), so quoted literals must include their quotes.
        IF i_default_values[l_index] IS NOT NULL THEN
            IF pg_catalog.btrim(i_default_values[l_index]) = '' THEN
                RAISE EXCEPTION
                    'ddl_utils_lib.add_columns: default value for column % is blank',
                    i_column_names[l_index]
                    USING ERRCODE = '22023';
            END IF;
            l_fragment := l_fragment || pg_catalog.format(' DEFAULT %s', i_default_values[l_index]);
        END IF;

        IF NOT i_nullable[l_index] THEN
            l_fragment := l_fragment || ' NOT NULL';
        END IF;
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

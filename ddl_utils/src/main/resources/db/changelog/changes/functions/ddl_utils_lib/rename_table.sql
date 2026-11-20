CREATE OR REPLACE FUNCTION ddl_utils_lib.rename_table(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_new_table_name ddl_utils.non_null_text,
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
    -- PostgreSQL truncates an identifier to NAMEDATALEN - 1 bytes with only a
    -- NOTICE, so a rename to an over-long name would appear to succeed while the
    -- lock-aware wrapper moved the table's settings to a name the table does not
    -- have.
    IF pg_catalog.octet_length(i_new_table_name) > 63 THEN
        RAISE EXCEPTION
            'ddl_utils_lib.rename_table: the new table name must be at most 63 bytes'
            USING ERRCODE = '22023';
    END IF;

    -- RENAME TO takes an unqualified name. A value such as 'other_schema.t2'
    -- would be quoted as one identifier and create a table literally named
    -- other_schema.t2 in the source schema.
    IF pg_catalog.strpos(i_new_table_name, '.') > 0 THEN
        RAISE EXCEPTION
            'ddl_utils_lib.rename_table: the new table name must be unqualified; the rename stays in the same schema'
            USING ERRCODE = '22023';
    END IF;

    -- Metadata-only. The new name is unqualified and stays in the same schema;
    -- both names are identifiers, so they are quoted with %I.
    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format('RENAME TO %I', i_new_table_name),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.rename_table IS
    'Renames a table, taking the lock settings explicitly. The new name must be '
        'unqualified and at most 63 bytes.';

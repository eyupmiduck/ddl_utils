CREATE OR REPLACE PROCEDURE ddl_utils.ensure_foreign_key(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
    i_referenced_schema_name ddl_utils.non_null_text,
    i_referenced_table_name ddl_utils.non_null_text,
    i_referenced_column_names ddl_utils.non_empty_non_null_text_array
)
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_lock_timeout       integer;
    l_sleep_time         integer;
    l_statement_duration integer;
    l_relation           regclass;
BEGIN
    -- Read the lock settings once; they are reused across both steps.
    SELECT ls.ddl_lock_timeout, ls.sleep_time, ls.statement_duration
    INTO l_lock_timeout, l_sleep_time, l_statement_duration
    FROM ddl_utils.get_lock_settings(
                 i_schema_name => i_schema_name,
                 i_table_name => i_table_name
         ) AS ls;

    l_relation := pg_catalog.format('%I.%I', i_schema_name, i_table_name)::regclass;

    -- A foreign key can be compared exactly, so a same-named key that references
    -- different columns or a different table is a mismatch rather than the
    -- target. Compare the referenced relation (confrelid) and both ordered
    -- column lists (conkey/confkey, attnum order); fail loudly on a mismatch so
    -- the wrong constraint is never silently accepted.
    IF EXISTS (SELECT 1
               FROM pg_catalog.pg_constraint
               WHERE conname = i_constraint_name
                   AND conrelid = l_relation
                   AND contype = 'f')
        AND NOT EXISTS (SELECT 1
                        FROM pg_catalog.pg_constraint AS c
                        WHERE c.conname = i_constraint_name
                            AND c.conrelid = l_relation
                            AND c.contype = 'f'
                            AND c.confrelid = pg_catalog.format(
                                '%I.%I', i_referenced_schema_name, i_referenced_table_name)::regclass
                            AND c.conkey = (
                                SELECT pg_catalog.array_agg(a.attnum ORDER BY t.ord)
                                FROM pg_catalog.unnest(i_column_names) WITH ORDINALITY AS t(name, ord)
                                JOIN pg_catalog.pg_attribute AS a
                                    ON a.attrelid = c.conrelid
                                        AND a.attname = t.name
                            )
                            AND c.confkey = (
                                SELECT pg_catalog.array_agg(a.attnum ORDER BY t.ord)
                                FROM pg_catalog.unnest(i_referenced_column_names) WITH ORDINALITY AS t(name, ord)
                                JOIN pg_catalog.pg_attribute AS a
                                    ON a.attrelid = c.confrelid
                                        AND a.attname = t.name
                            )) THEN
        RAISE EXCEPTION
            'ddl_utils.ensure_foreign_key: constraint % already exists on %.% with a different definition',
            i_constraint_name, i_schema_name, i_table_name
            USING ERRCODE = '42710';
    END IF;

    -- Step 1: add the foreign key as NOT VALID (no scan; SHARE ROW EXCLUSIVE on
    -- both tables, released at the commit). Skipped when it already exists, so a
    -- re-run recovers after a partial failure.
    IF NOT EXISTS (SELECT 1
                   FROM pg_catalog.pg_constraint
                   WHERE conname = i_constraint_name
                       AND conrelid = l_relation
                       AND contype = 'f') THEN
        PERFORM ddl_utils_lib.add_foreign_key(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => i_constraint_name,
                i_column_names => i_column_names,
                i_referenced_schema_name => i_referenced_schema_name,
                i_referenced_table_name => i_referenced_table_name,
                i_referenced_column_names => i_referenced_column_names,
                i_ddl_lock_timeout => l_lock_timeout,
                i_sleep_time => l_sleep_time,
                i_statement_duration => l_statement_duration
                );
        COMMIT;
    END IF;

    -- Step 2: validate the constraint, scanning under SHARE UPDATE EXCLUSIVE.
    IF EXISTS (SELECT 1
               FROM pg_catalog.pg_constraint
               WHERE conname = i_constraint_name
                   AND conrelid = l_relation
                   AND contype = 'f'
                   AND NOT convalidated) THEN
        PERFORM ddl_utils_lib.validate_constraint(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => i_constraint_name,
                i_ddl_lock_timeout => l_lock_timeout,
                i_sleep_time => l_sleep_time,
                i_statement_duration => l_statement_duration
                );
        COMMIT;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION ddl_utils_lib.add_primary_key_using_index(
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
            'ddl_utils_lib.add_primary_key_using_index: the constraint name must be at most 63 bytes'
            USING ERRCODE = '22023';
    END IF;

    PERFORM ddl_utils_lib.validate_unique_index_for_attach(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_index_name => i_index_name,
            i_context => 'ddl_utils_lib.add_primary_key_using_index');

    -- Attaching is metadata-only only when the index is valid, unique,
    -- non-partial and non-expression, and every key column is already NOT NULL.
    -- On PostgreSQL 18+ a catalogued NOT NULL constraint can be NOT VALID while
    -- attnotnull is already true; the extra EXISTS rejects that case so the
    -- ALTER cannot fall into a validating scan or an "incompatible NOT VALID
    -- constraint" error. On older releases the EXISTS is always false.
    IF EXISTS (SELECT 1
               FROM pg_catalog.pg_class AS i
                        JOIN pg_catalog.pg_namespace AS n ON n.oid = i.relnamespace
                        JOIN pg_catalog.pg_index AS ix ON ix.indexrelid = i.oid
                        CROSS JOIN LATERAL pg_catalog.generate_subscripts(ix.indkey, 1) AS s(i)
                        JOIN pg_catalog.pg_attribute AS a
                             ON a.attrelid = ix.indrelid AND a.attnum = ix.indkey[s.i]
               WHERE n.nspname = i_schema_name
                 AND i.relname = i_index_name
                 AND s.i < ix.indnkeyatts
                 AND (NOT a.attnotnull
                      OR EXISTS (SELECT 1
                                 FROM pg_catalog.pg_constraint AS c
                                 WHERE c.conrelid = a.attrelid
                                   AND c.contype = 'n'
                                   AND NOT c.convalidated
                                   AND a.attnum = ANY (c.conkey)))) THEN
        RAISE EXCEPTION
            'ddl_utils_lib.add_primary_key_using_index: every indexed column of %.% must already be NOT NULL and validated; use ensure_not_null first',
            i_schema_name, i_table_name
            USING ERRCODE = '55000';
    END IF;

    -- Attaches an already-built unique index as the primary key, which is
    -- metadata-only when the indexed columns are already NOT NULL and the index
    -- is valid. Build the index with CREATE UNIQUE INDEX CONCURRENTLY first so
    -- the table is never held under ACCESS EXCLUSIVE for the build.
    PERFORM ddl_utils_lib.alter_table(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_alter_table_fragment => pg_catalog.format(
                    'ADD CONSTRAINT %I PRIMARY KEY USING INDEX %I',
                    i_constraint_name,
                    i_index_name
                                      ),
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.add_primary_key_using_index IS
    'Attaches a pre-built unique index as the primary key. The index and NOT NULL '
        'preconditions are validated before the ALTER.';

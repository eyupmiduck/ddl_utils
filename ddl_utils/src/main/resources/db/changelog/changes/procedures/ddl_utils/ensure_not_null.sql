CREATE OR REPLACE PROCEDURE ddl_utils.ensure_not_null(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_column_name ddl_utils.non_null_text
)
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_settings         ddl_utils.lock_settings;
    l_already_not_null boolean;
    l_base             text;
    l_constraint_name  text;
    l_relation         regclass;
    l_lock_class       integer;
    l_lock_key         integer;
BEGIN
    -- Read the lock settings once; they are reused across every step.
    SELECT *
    INTO l_settings
    FROM ddl_utils.get_lock_settings(
                 i_schema_name => i_schema_name,
                 i_table_name => i_table_name
         ) AS ls;

    l_relation := pg_catalog.format('%I.%I', i_schema_name, i_table_name)::regclass;

    -- A transaction-scoped advisory lock on this table and column serializes
    -- concurrent runs. The read-then-act guards are separated by COMMIT, so the
    -- lock is taken again before each step; it is released by that step's
    -- COMMIT and on error, so a failed run cannot leak it.
    l_lock_class := pg_catalog.hashtext('ddl_utils.ensure_not_null');
    l_lock_key := pg_catalog.hashtext(
            pg_catalog.format('%I.%I.%I', i_schema_name, i_table_name, i_column_name));

    -- Fail with a clear error rather than an opaque undefined_column from the
    -- generated CHECK when the column does not exist.
    IF NOT EXISTS (SELECT 1
                   FROM pg_catalog.pg_attribute
                   WHERE attrelid = l_relation
                     AND attname = i_column_name
                     AND attnum > 0
                     AND NOT attisdropped) THEN
        RAISE EXCEPTION 'ddl_utils.ensure_not_null: column %.% does not exist',
            i_table_name, i_column_name
            USING ERRCODE = '42703';
    END IF;

    -- A deterministic, collision-resistant name (<= 63 bytes) so a re-run after
    -- a partial failure finds the same temporary constraint instead of making a
    -- second one. Replace non-ASCII characters before truncating: left() counts
    -- characters, so a multibyte name could still exceed the 63-byte identifier
    -- limit and be truncated by ADD CONSTRAINT, breaking the re-run lookup.
    l_base := pg_catalog.regexp_replace(
            pg_catalog.format('%s_%s_not_null', i_table_name, i_column_name),
            '[^A-Za-z0-9_]', '_', 'g');
    l_constraint_name := pg_catalog.left(l_base, 45)
                             || '_' || pg_catalog.substr(pg_catalog.md5(
                                                                 pg_catalog.format('%s.%s.%s',
                                                                                   i_schema_name,
                                                                                   i_table_name,
                                                                                   i_column_name)),
                                                         1, 8);

    -- An already-NOT-NULL column needs no proof, so skip the add/validate/set
    -- steps and do not take a fresh ACCESS EXCLUSIVE lock or re-scan the table.
    -- Step 4 still runs, to clean up any temporary constraint left by a partial
    -- failure after SET NOT NULL had committed.
    SELECT a.attnotnull
    INTO l_already_not_null
    FROM pg_catalog.pg_attribute AS a
    WHERE a.attrelid = l_relation
      AND a.attname = i_column_name
      AND a.attnum > 0
      AND NOT a.attisdropped;

    -- The existence check above already rejects a missing column; guard the
    -- flag anyway so a NULL can never make every step below a silent no-op.
    IF NOT FOUND THEN
        RAISE EXCEPTION 'ddl_utils.ensure_not_null: column %.% does not exist',
            i_table_name, i_column_name
            USING ERRCODE = '42703';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_class, l_lock_key);
    -- Step 1: add the proof as NOT VALID (instant; a brief ACCESS EXCLUSIVE
    -- lock). Skipped when the column is already NOT NULL or the constraint
    -- already exists, which is how a re-run recovers.
    IF NOT l_already_not_null
        AND NOT EXISTS (SELECT 1
                        FROM pg_catalog.pg_constraint
                        WHERE conname = l_constraint_name
                          AND conrelid = l_relation
                          AND contype = 'c') THEN
        PERFORM ddl_utils_lib.add_check_constraint(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => l_constraint_name,
                i_check_expression => pg_catalog.format('%I IS NOT NULL', i_column_name),
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration
                );
        COMMIT;
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_class, l_lock_key);
    -- Step 2: validate the constraint, scanning under SHARE UPDATE EXCLUSIVE.
    -- A NOT VALID constraint with existing NULLs fails here with 23514, leaving
    -- the constraint in place so the caller can fix the data and re-run.
    IF NOT l_already_not_null
        AND EXISTS (SELECT 1
                    FROM pg_catalog.pg_constraint
                    WHERE conname = l_constraint_name
                      AND conrelid = l_relation
                      AND contype = 'c'
                      AND NOT convalidated) THEN
        PERFORM ddl_utils_lib.validate_constraint(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => l_constraint_name,
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration
                );
        COMMIT;
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_class, l_lock_key);
    -- Step 3: SET NOT NULL. The valid CHECK lets PostgreSQL skip its own scan.
    IF NOT l_already_not_null THEN
        PERFORM ddl_utils_lib.set_not_null(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_column_name => i_column_name,
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration
                );
        COMMIT;
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_class, l_lock_key);
    -- Step 4: remove the temporary proof constraint.
    IF EXISTS (SELECT 1
               FROM pg_catalog.pg_constraint
               WHERE conname = l_constraint_name
                 AND conrelid = l_relation
                 AND contype = 'c') THEN
        PERFORM ddl_utils_lib.drop_constraint(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => l_constraint_name,
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration
                );
        COMMIT;
    END IF;
END;
$$;

COMMENT ON PROCEDURE ddl_utils.ensure_not_null IS
    'Makes a column NOT NULL without holding ACCESS EXCLUSIVE across the scan.';

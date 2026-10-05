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
    l_name_taken       boolean;
    l_proof_exists     boolean;
    l_base             text;
    l_constraint_name  text;
    l_relation         regclass;
    l_lock_key         bigint;
BEGIN
    -- Read the lock settings once; they are reused across every step.
    SELECT *
    INTO l_settings
    FROM ddl_utils.get_lock_settings(
                 i_schema_name => i_schema_name,
                 i_table_name => i_table_name
         ) AS ls;

    l_relation := pg_catalog.format('%I.%I', i_schema_name, i_table_name)::regclass;

    -- A named relation with an attribute is not necessarily a table a CHECK
    -- constraint and SET NOT NULL can be added to; reject other relation kinds
    -- (views, sequences, ...) before the commit-separated workflow starts.
    IF (SELECT c.relkind
        FROM pg_catalog.pg_class AS c
        WHERE c.oid = l_relation) NOT IN ('r', 'p') THEN
        RAISE EXCEPTION 'ddl_utils.ensure_not_null: %.% is not a table',
            i_schema_name, i_table_name
            USING ERRCODE = '42809';
    END IF;

    -- A transaction-scoped advisory lock on this table and column serializes
    -- concurrent runs. The read-then-act guards are separated by COMMIT, so the
    -- lock is taken again before each step; it is released by that step's
    -- COMMIT and on error, so a failed run cannot leak it. A 64-bit key avoids
    -- unrelated tables/columns colliding on the same lock.
    l_lock_key := pg_catalog.hashtextextended(
            pg_catalog.format('ddl_utils.ensure_not_null|%I.%I.%I',
                              i_schema_name, i_table_name, i_column_name),
            0);

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

    -- Take the advisory lock before reading the column state, so two callers
    -- cannot both observe a nullable column and then both run the scan.
    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_key);

    -- The generated name is this routine's identity for its temporary proof.
    -- Refuse a same-named constraint that is not the expected <column> IS NOT
    -- NULL proof, so a pre-existing constraint is never validated, trusted as
    -- proof, or dropped.
    l_name_taken := EXISTS (SELECT 1
                            FROM pg_catalog.pg_constraint AS c
                            WHERE c.conname = l_constraint_name
                              AND c.conrelid = l_relation);

    l_proof_exists := EXISTS (SELECT 1
                              FROM pg_catalog.pg_constraint AS c
                              WHERE c.conname = l_constraint_name
                                AND c.conrelid = l_relation
                                AND c.contype = 'c'
                                AND pg_catalog.pg_get_expr(c.conbin, c.conrelid) =
                                    pg_catalog.format('(%I IS NOT NULL)', i_column_name));

    IF l_name_taken AND NOT l_proof_exists THEN
        RAISE EXCEPTION
            'ddl_utils.ensure_not_null: constraint % already exists on %.% with a different definition',
            l_constraint_name, i_schema_name, i_table_name
            USING ERRCODE = '42710';
    END IF;

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

    -- Step 1: add the proof as NOT VALID (instant; a brief ACCESS EXCLUSIVE
    -- lock). Skipped when the column is already NOT NULL or the constraint
    -- already exists, which is how a re-run recovers.
    IF NOT l_already_not_null
        AND NOT l_proof_exists THEN
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
        l_proof_exists := true;
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_key);
    -- Step 2: validate the constraint, scanning under SHARE UPDATE EXCLUSIVE.
    -- A NOT VALID constraint with existing NULLs fails here with 23514, leaving
    -- the constraint in place so the caller can fix the data and re-run.
    IF NOT l_already_not_null
        AND l_proof_exists
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

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_key);
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

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_key);
    -- Step 4: remove the temporary proof constraint (only ever this routine's
    -- own proof; a foreign same-named constraint was rejected above).
    IF l_proof_exists THEN
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

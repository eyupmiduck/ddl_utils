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
    l_settings            ddl_utils.lock_settings;
    l_referenced_settings ddl_utils.lock_settings;
    l_relation            regclass;
    l_referenced_relation regclass;
    l_constraint_oid      oid;
    l_existing_definition text;
    l_column_name         text;
BEGIN
    -- PostgreSQL truncates an identifier to NAMEDATALEN - 1 bytes with only a
    -- NOTICE. The catalog guards below compare conname with the untruncated
    -- argument, so an over-long name would be added under a truncated name,
    -- committed, and then fail to match on the validation/recovery path.
    IF pg_catalog.octet_length(i_constraint_name) > 63 THEN
        RAISE EXCEPTION
            'ddl_utils.ensure_foreign_key: the constraint name must be at most 63 bytes'
            USING ERRCODE = '22023';
    END IF;

    -- The resolver also casts to ddl_utils.identifier, but check here first so
    -- an over-long name names the offending argument instead of surfacing the
    -- domain's generic 23514.
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_schema_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_argument => 'i_schema_name');
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_table_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_argument => 'i_table_name');
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_referenced_schema_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_argument => 'i_referenced_schema_name');
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_referenced_table_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_argument => 'i_referenced_table_name');

    FOREACH l_column_name IN ARRAY i_column_names::text[]
        LOOP
            PERFORM ddl_utils_lib.assert_identifier_length(
                    i_value => l_column_name,
                    i_context => 'ddl_utils.ensure_foreign_key',
                    i_argument => 'i_column_names');
        END LOOP;
    FOREACH l_column_name IN ARRAY i_referenced_column_names::text[]
        LOOP
            PERFORM ddl_utils_lib.assert_identifier_length(
                    i_value => l_column_name,
                    i_context => 'ddl_utils.ensure_foreign_key',
                    i_argument => 'i_referenced_column_names');
        END LOOP;

    -- Read both tables' lock settings once; they are reused across both steps.
    l_settings := ddl_utils_lib.table_lock_settings(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name);
    l_referenced_settings := ddl_utils_lib.table_lock_settings(
            i_schema_name => i_referenced_schema_name,
            i_table_name => i_referenced_table_name);

    l_relation := ddl_utils_lib.resolve_table_relation(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_relation_label => 'table');

    -- The definitions are compared as a column mapping, which only makes sense
    -- for equal-length lists; reject mismatched lengths up front instead of
    -- silently dropping the surplus when they are zipped.
    PERFORM ddl_utils_lib.assert_equal_cardinality(
            i_a => i_column_names,
            i_b => i_referenced_column_names,
            i_context => 'ddl_utils.ensure_foreign_key: column lists');

    -- PostgreSQL requires a foreign key's column lists to name distinct
    -- columns; the join-based existence checks below would not notice a repeat,
    -- so reject duplicates before comparing or adding.
    IF pg_catalog.cardinality(i_column_names) <> (SELECT pg_catalog.count(DISTINCT name)
                                                  FROM pg_catalog.unnest(i_column_names) AS t(name))
        OR pg_catalog.cardinality(i_referenced_column_names) <>
           (SELECT pg_catalog.count(DISTINCT name)
            FROM pg_catalog.unnest(i_referenced_column_names) AS t(name)) THEN
        RAISE EXCEPTION 'ddl_utils.ensure_foreign_key: column lists must not contain duplicates'
            USING ERRCODE = '22023';
    END IF;

    -- A transaction-scoped advisory lock on the pair of relations serializes
    -- concurrent runs, including reciprocal ensure_foreign_key(A -> B) and
    -- (B -> A): ADD FOREIGN KEY takes SHARE ROW EXCLUSIVE on both relations, so
    -- a source-only key lets reciprocal calls acquire the table locks in
    -- opposite order and deadlock. operation_lock_key sorts the keys, so both
    -- directions use the same lock. The lock is released by each step's COMMIT
    -- and on error, and step 2 re-takes it after step 1's COMMIT.
    PERFORM ddl_utils_lib.acquire_operation_lock(
            i_context => 'ddl_utils.ensure_foreign_key',
            i_keys => ARRAY [
                pg_catalog.format('%I.%I', i_schema_name, i_table_name),
                pg_catalog.format('%I.%I', i_referenced_schema_name, i_referenced_table_name)
                ]);

    l_referenced_relation := ddl_utils_lib.resolve_table_relation(
            i_schema_name => i_referenced_schema_name,
            i_table_name => i_referenced_table_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_relation_label => 'referenced table');

    -- A named column that does not resolve must fail loudly. Otherwise the
    -- pg_attribute join in the definition check silently drops it and reports a
    -- misleading "already exists with a different definition".
    PERFORM ddl_utils_lib.assert_columns_exist(
            i_relation => l_relation,
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_column_names => i_column_names,
            i_context => 'ddl_utils.ensure_foreign_key');
    PERFORM ddl_utils_lib.assert_columns_exist(
            i_relation => l_referenced_relation,
            i_schema_name => i_referenced_schema_name,
            i_table_name => i_referenced_table_name,
            i_column_names => i_referenced_column_names,
            i_context => 'ddl_utils.ensure_foreign_key');

    -- Even a NOT VALID foreign key requires the referenced columns to be
    -- covered by a unique index (a PRIMARY KEY/UNIQUE constraint or a bare
    -- unique index). Without this guard the generic "there is no unique
    -- constraint matching given keys" comes from ALTER TABLE instead.
    IF NOT EXISTS (SELECT 1
                   FROM pg_catalog.pg_index AS ix
                   WHERE ix.indrelid = l_referenced_relation
                     AND ix.indisunique
                     AND ix.indisvalid
                     AND ix.indpred IS NULL
                     AND ix.indexprs IS NULL
                     AND ix.indnkeyatts = pg_catalog.cardinality(i_referenced_column_names)
                     AND (SELECT pg_catalog.count(*)
                          FROM pg_catalog.generate_subscripts(ix.indkey, 1) AS s(i)
                                   JOIN pg_catalog.pg_attribute AS a
                                        ON a.attrelid = ix.indrelid AND a.attnum = ix.indkey[s.i]
                          WHERE s.i < ix.indnkeyatts
                            AND a.attname = ANY (i_referenced_column_names::text[]))
                       = pg_catalog.cardinality(i_referenced_column_names)) THEN
        RAISE EXCEPTION
            'ddl_utils.ensure_foreign_key: the referenced columns of %.% are not covered by a unique index or constraint',
            i_referenced_schema_name, i_referenced_table_name
            USING ERRCODE = '42830';
    END IF;

    SELECT s.constraint_oid
    INTO l_constraint_oid
    FROM ddl_utils_lib.constraint_state(
                 i_relation => l_relation,
                 i_constraint_name => i_constraint_name,
                 i_contype => 'f') AS s;

    -- A foreign key can be compared exactly, so a same-named key that references
    -- a different table, maps different columns, or has different referential
    -- actions/deferrability is a mismatch rather than the target; fail loudly so
    -- the wrong constraint is never silently accepted.
    IF l_constraint_oid IS NOT NULL
        AND NOT ddl_utils_lib.foreign_key_definition_matches(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => i_constraint_name,
                i_referenced_schema_name => i_referenced_schema_name,
                i_referenced_table_name => i_referenced_table_name,
                i_column_names => i_column_names,
                i_referenced_column_names => i_referenced_column_names) THEN
        l_existing_definition := pg_catalog.pg_get_constraintdef(l_constraint_oid);

        RAISE EXCEPTION
            'ddl_utils.ensure_foreign_key: constraint % already exists on %.% with a different definition (existing: %)',
            i_constraint_name, i_schema_name, i_table_name, l_existing_definition
            USING ERRCODE = '42710';
    END IF;

    -- A same-named constraint of another kind (PRIMARY KEY, UNIQUE, CHECK,
    -- EXCLUDE) would make add_foreign_key fail with a generic duplicate-object
    -- error; name the conflict instead.
    IF ddl_utils_lib.constraint_of_other_kind_exists(
            i_relation => l_relation,
            i_constraint_name => i_constraint_name,
            i_contype => 'f') THEN
        RAISE EXCEPTION
            'ddl_utils.ensure_foreign_key: constraint % already exists on %.% as a different kind of constraint',
            i_constraint_name, i_schema_name, i_table_name
            USING ERRCODE = '42710';
    END IF;

    -- Step 1: add the foreign key as NOT VALID (no scan; SHARE ROW EXCLUSIVE on
    -- both tables, released at the commit). Skipped when it already exists, so a
    -- re-run recovers after a partial failure. Pre-lock both tables first with
    -- each table's own settings; the advisory lock above serializes concurrent
    -- calls but does not bound the table-lock wait.
    IF l_constraint_oid IS NULL THEN
        PERFORM ddl_utils_lib.lock_foreign_key_tables(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_lock_mode => 'share row exclusive',
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration,
                i_referenced_schema_name => i_referenced_schema_name,
                i_referenced_table_name => i_referenced_table_name,
                i_referenced_lock_mode => 'share row exclusive',
                i_referenced_ddl_lock_timeout => l_referenced_settings.ddl_lock_timeout,
                i_referenced_sleep_time => l_referenced_settings.sleep_time,
                i_referenced_statement_duration => l_referenced_settings.statement_duration
                );

        PERFORM ddl_utils_lib.add_foreign_key(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => i_constraint_name,
                i_column_names => i_column_names,
                i_referenced_schema_name => i_referenced_schema_name,
                i_referenced_table_name => i_referenced_table_name,
                i_referenced_column_names => i_referenced_column_names,
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration
                );
        COMMIT;
    END IF;

    PERFORM ddl_utils_lib.acquire_operation_lock(
            i_context => 'ddl_utils.ensure_foreign_key',
            i_keys => ARRAY [
                pg_catalog.format('%I.%I', i_schema_name, i_table_name),
                pg_catalog.format('%I.%I', i_referenced_schema_name, i_referenced_table_name)
                ]);

    -- The COMMIT released the lock, so re-resolve both relations and re-verify
    -- the constraint before validating it. External DDL is not blocked by the
    -- advisory lock.
    l_relation := ddl_utils_lib.resolve_table_relation(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_relation_label => 'table');

    l_referenced_relation := ddl_utils_lib.resolve_table_relation(
            i_schema_name => i_referenced_schema_name,
            i_table_name => i_referenced_table_name,
            i_context => 'ddl_utils.ensure_foreign_key',
            i_relation_label => 'referenced table');

    SELECT s.constraint_oid
    INTO l_constraint_oid
    FROM ddl_utils_lib.constraint_state(
                 i_relation => l_relation,
                 i_constraint_name => i_constraint_name,
                 i_contype => 'f') AS s;

    IF l_constraint_oid IS NULL THEN
        RAISE EXCEPTION
            'ddl_utils.ensure_foreign_key: constraint % no longer exists on %.%',
            i_constraint_name, i_schema_name, i_table_name
            USING ERRCODE = '42704';
    END IF;

    -- The comparison is by the caller's column names, not by attnum: a source
    -- or referenced column renamed between steps updates the stored definition
    -- to the new name, so the mapping no longer matches the requested names.
    -- Fail closed (42710) and leave the key NOT VALID rather than validate a
    -- key the caller may not recognize; a re-run with the new names validates
    -- it. This misreports a rename as a definition change, but never silently
    -- accepts or validates the wrong mapping.
    IF NOT ddl_utils_lib.foreign_key_definition_matches(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_constraint_name => i_constraint_name,
            i_referenced_schema_name => i_referenced_schema_name,
            i_referenced_table_name => i_referenced_table_name,
            i_column_names => i_column_names,
            i_referenced_column_names => i_referenced_column_names) THEN
        l_existing_definition := pg_catalog.pg_get_constraintdef(l_constraint_oid);

        RAISE EXCEPTION
            'ddl_utils.ensure_foreign_key: constraint % changed definition on %.% between steps (existing: %)',
            i_constraint_name, i_schema_name, i_table_name, l_existing_definition
            USING ERRCODE = '42710';
    END IF;

    -- Step 2: validate the constraint, scanning under SHARE UPDATE EXCLUSIVE.
    -- Validating a foreign key also takes SHARE ROW EXCLUSIVE on the referenced
    -- table, so pre-lock both with each table's own settings: the source lock
    -- does not block DML, while the referenced lock matches what VALIDATE takes.
    IF EXISTS (SELECT 1
               FROM pg_catalog.pg_constraint AS c
               WHERE c.oid = l_constraint_oid
                 AND NOT c.convalidated) THEN
        PERFORM ddl_utils_lib.lock_foreign_key_tables(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_lock_mode => 'share update exclusive',
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration,
                i_referenced_schema_name => i_referenced_schema_name,
                i_referenced_table_name => i_referenced_table_name,
                i_referenced_lock_mode => 'share row exclusive',
                i_referenced_ddl_lock_timeout => l_referenced_settings.ddl_lock_timeout,
                i_referenced_sleep_time => l_referenced_settings.sleep_time,
                i_referenced_statement_duration => l_referenced_settings.statement_duration
                );

        PERFORM ddl_utils_lib.validate_constraint(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => i_constraint_name,
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration
                );
        COMMIT;
    END IF;
END;
$$;

COMMENT ON PROCEDURE ddl_utils.ensure_foreign_key IS
    'Adds a foreign key as NOT VALID and then validates it, committing between '
        'steps. The add and validate steps pre-lock both tables with each '
        'table''s own settings. The referenced columns must be covered by a '
        'unique index or constraint. Call it outside a transaction block '
        '(autocommit).';

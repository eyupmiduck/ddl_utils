CREATE OR REPLACE PROCEDURE ddl_utils.ensure_check_constraint(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_check_expression ddl_utils.non_null_text
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
    l_lock_class         integer;
    l_lock_key           integer;
BEGIN
    -- Read the lock settings once; they are reused across both steps.
    SELECT ls.ddl_lock_timeout, ls.sleep_time, ls.statement_duration
    INTO l_lock_timeout, l_sleep_time, l_statement_duration
    FROM ddl_utils.get_lock_settings(
                 i_schema_name => i_schema_name,
                 i_table_name => i_table_name
         ) AS ls;

    l_relation := pg_catalog.format('%I.%I', i_schema_name, i_table_name)::regclass;

    -- A transaction-scoped advisory lock on this table serializes concurrent
    -- runs. The read-then-act guards are separated by COMMIT, so the lock is
    -- taken again before each step; it is released by that step's COMMIT and on
    -- error, so a failed run cannot leak it.
    l_lock_class := pg_catalog.hashtext('ddl_utils.ensure_check_constraint');
    l_lock_key := pg_catalog.hashtext(pg_catalog.format('%I.%I', i_schema_name, i_table_name));

    -- The name is the identity: a same-named CHECK is treated as the target and
    -- its expression is not re-checked. Unlike a foreign key, the definition
    -- cannot be compared exactly -- pg_get_constraintdef returns a normalized,
    -- version-dependent form of the expression -- so a same-named constraint
    -- with a different expression is not detected. Use a distinct name per
    -- expression.
    --
    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_class, l_lock_key);
    -- Step 1: add the constraint as NOT VALID (instant; brief ACCESS
    -- EXCLUSIVE). Skipped when it already exists, which is how a re-run
    -- recovers after the add committed but validation did not.
    IF NOT EXISTS (SELECT 1
                   FROM pg_catalog.pg_constraint
                   WHERE conname = i_constraint_name
                     AND conrelid = l_relation
                     AND contype = 'c') THEN
        PERFORM ddl_utils_lib.add_check_constraint(
                i_schema_name => i_schema_name,
                i_table_name => i_table_name,
                i_constraint_name => i_constraint_name,
                i_check_expression => i_check_expression,
                i_ddl_lock_timeout => l_lock_timeout,
                i_sleep_time => l_sleep_time,
                i_statement_duration => l_statement_duration
                );
        COMMIT;
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(l_lock_class, l_lock_key);
    -- Step 2: validate the constraint, scanning under SHARE UPDATE EXCLUSIVE.
    IF EXISTS (SELECT 1
               FROM pg_catalog.pg_constraint
               WHERE conname = i_constraint_name
                 AND conrelid = l_relation
                 AND contype = 'c'
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

COMMENT ON PROCEDURE ddl_utils.ensure_check_constraint IS
    'Adds a CHECK constraint as NOT VALID and then validates it, committing between steps.';

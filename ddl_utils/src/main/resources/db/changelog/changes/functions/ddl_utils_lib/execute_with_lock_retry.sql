CREATE OR REPLACE FUNCTION ddl_utils_lib.execute_with_lock_retry(
    i_statement ddl_utils.non_null_text,
    i_context ddl_utils.non_null_text,
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
    l_started_at              timestamptz;
    l_previous_lock_timeout   text;
    l_previous_application_name text;
    l_attempts                integer := 0;
    l_elapsed_ms              integer;
BEGIN
    -- The only routine that executes dynamic SQL. alter_table builds the
    -- statement from a validated fragment, and lock_foreign_key_tables builds it
    -- from validated identifiers and an allow-listed lock mode. The function is
    -- SECURITY INVOKER, so a direct caller already holds the privileges the
    -- statement uses. PlpgsqlCheckTest allow-lists the security warning for the
    -- EXECUTE below.
    l_started_at := pg_catalog.clock_timestamp();
    -- Remember the caller's settings so a successful call does not change them
    -- for the rest of the caller's transaction.
    l_previous_lock_timeout := pg_catalog.current_setting('lock_timeout');
    l_previous_application_name := pg_catalog.current_setting('application_name');

    -- Mark the session for the duration of the operation. application_name is
    -- visible in pg_stat_activity from another session, so an operator can see
    -- which helper/table is in flight. The caller's value is restored on
    -- success; an error rolls the transaction-local setting back.
    PERFORM pg_catalog.set_config(
            'application_name',
            pg_catalog.left('ddl_utils:' || i_context, 63),
            true
            );

    LOOP
        l_attempts := l_attempts + 1;
        -- lock_timeout is transaction scoped; set it outside the exception
        -- block on every attempt so a caught failure cannot leave it unset.
        -- (Setting it inside the block would be rolled back with the
        -- subtransaction.) A ddl_lock_timeout of 0 waits with no timeout (and
        -- a sleep_time of 0 busy-waits); the statement budget then cannot
        -- apply.
        --
        -- i_statement_duration is a lock-acquisition retry budget, not a limit
        -- on how long the statement may run once it has its locks: a long scan
        -- or rewrite is intentional. It is checked after each failed attempt,
        -- so one attempt can block for up to i_ddl_lock_timeout and a retry can
        -- overshoot the budget by that plus one i_sleep_time.
        PERFORM pg_catalog.set_config(
                'lock_timeout',
                pg_catalog.format('%sms', i_ddl_lock_timeout),
                true
                );

        BEGIN
            EXECUTE i_statement;
            -- Only the success path needs to restore: set_config(..., true) is
            -- transaction-local and a raised error rolls back the enclosing
            -- (sub)transaction, which reverts the GUCs anyway.
            PERFORM pg_catalog.set_config('lock_timeout', l_previous_lock_timeout, true);
            PERFORM pg_catalog.set_config('application_name', l_previous_application_name, true);
            RETURN;
        EXCEPTION
            WHEN lock_not_available THEN
                IF pg_catalog.clock_timestamp() - l_started_at
                    >= i_statement_duration * interval '1 millisecond' THEN
                    l_elapsed_ms := pg_catalog.round(
                            pg_catalog.date_part('epoch', pg_catalog.clock_timestamp() - l_started_at)
                                * 1000)::integer;
                    RAISE EXCEPTION
                        '% after % attempt(s) in % ms (retry budget % ms)',
                        i_context,
                        l_attempts,
                        l_elapsed_ms,
                        i_statement_duration
                        USING ERRCODE = '55P03';
                END IF;
        END;

        PERFORM pg_catalog.pg_sleep(i_sleep_time::double precision / 1000);
    END LOOP;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.execute_with_lock_retry IS
    'Internal runner: executes one statement with a bounded lock_timeout and a '
        'retry budget (i_statement_duration). The budget bounds lock acquisition '
        'only; a statement that has its locks may run for as long as it needs. '
        'A 55P03 reports the attempt count and elapsed time, and the session '
        'application_name is set to a ddl_utils marker while the call runs and '
        'restored on success.';

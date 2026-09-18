CREATE OR REPLACE FUNCTION ddl_utils_lib.alter_table(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_alter_table_fragment ddl_utils.non_null_text,
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
    l_statement             text;
    l_started_at            timestamptz;
    l_previous_lock_timeout text;
    l_lock_timeout          integer;
    l_remaining_ms          integer;
BEGIN
    -- The EXECUTE below is intentionally not sanitised: the fragment is
    -- caller-provided DDL and the function is SECURITY INVOKER (see the guard
    -- and its comment). Tell plpgsql_check so it does not report the expected
    -- SQL-injection warning for this function.
    -- @plpgsql_check_options: security_warnings = false
    IF pg_catalog.btrim(i_alter_table_fragment) = '' THEN
        RAISE EXCEPTION 'ddl_utils_lib.alter_table: the alter table fragment must not be blank'
            USING ERRCODE = '22023';
    END IF;

    -- The fragment is arbitrary DDL, so it cannot be parameterised. Reject
    -- anything that could terminate the statement or inject a comment; real
    -- fragments (for example ADD COLUMN x int DEFAULT 'a') contain none of
    -- these. This is a best-effort guard: the function is SECURITY INVOKER, so
    -- the caller already holds the privileges the fragment would use.
    IF i_alter_table_fragment ~ '[;$]'
        OR i_alter_table_fragment LIKE '%--%'
        OR i_alter_table_fragment LIKE '%/*%'
        OR i_alter_table_fragment LIKE '%*/%' THEN
        RAISE EXCEPTION 'ddl_utils_lib.alter_table: the alter table fragment contains a statement separator or comment'
            USING ERRCODE = '22023';
    END IF;

    l_statement := pg_catalog.format(
            'ALTER TABLE %I.%I %s',
            i_schema_name,
            i_table_name,
            pg_catalog.btrim(i_alter_table_fragment)
                   );
    l_started_at := pg_catalog.clock_timestamp();
    -- Remember the caller's lock_timeout so a successful call does not change
    -- the setting for the rest of the caller's transaction.
    l_previous_lock_timeout := pg_catalog.current_setting('lock_timeout');

    LOOP
        -- lock_timeout is transaction scoped; set it outside the exception
        -- block on every attempt so a caught failure cannot leave it unset.
        -- (Setting it inside the block would be rolled back with the
        -- subtransaction.) The per-attempt timeout is capped by the remaining
        -- statement budget, so a single attempt cannot overshoot the deadline.
        -- A ddl_lock_timeout of 0 is a deliberate caller choice to wait with
        -- no timeout (and a sleep_time of 0 busy-waits); the budget then
        -- cannot apply.
        IF i_ddl_lock_timeout = 0 THEN
            l_lock_timeout := 0;
        ELSE
            l_remaining_ms := i_statement_duration - pg_catalog.floor(
                    pg_catalog.date_part('epoch', pg_catalog.clock_timestamp() - l_started_at) * 1000
                                                     )::integer;
            l_lock_timeout := CASE
                                  WHEN l_remaining_ms < 1 THEN 1
                                  WHEN l_remaining_ms < i_ddl_lock_timeout THEN l_remaining_ms
                                  ELSE i_ddl_lock_timeout
                END;
        END IF;
        PERFORM pg_catalog.set_config(
                'lock_timeout',
                pg_catalog.format('%sms', l_lock_timeout),
                true
                );

        BEGIN
            EXECUTE l_statement;
            PERFORM pg_catalog.set_config('lock_timeout', l_previous_lock_timeout, true);
            RETURN;
        EXCEPTION
            WHEN lock_not_available THEN
                IF pg_catalog.clock_timestamp() - l_started_at
                    >= i_statement_duration * interval '1 millisecond' THEN
                    RAISE EXCEPTION
                        'ddl_utils_lib.alter_table: could not acquire a lock on %.% within % ms',
                        i_schema_name, i_table_name, i_statement_duration
                        USING ERRCODE = '55P03';
                END IF;
        END;

        PERFORM pg_catalog.pg_sleep(i_sleep_time::double precision / 1000);
    END LOOP;
END;
$$;

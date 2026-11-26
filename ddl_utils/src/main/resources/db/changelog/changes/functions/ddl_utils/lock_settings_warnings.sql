CREATE OR REPLACE FUNCTION ddl_utils.lock_settings_warnings(
    i_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_sleep_time ddl_utils.non_negative_integer,
    i_statement_duration ddl_utils.non_negative_integer
)
    RETURNS text[]
    LANGUAGE sql
    IMMUTABLE
    SECURITY INVOKER
AS
$$
    SELECT pg_catalog.array_remove(
            ARRAY [
                CASE WHEN i_ddl_lock_timeout = 0
                    THEN 'per-attempt lock timeout is disabled; one attempt can block indefinitely' END,
                CASE WHEN i_sleep_time = 0
                    THEN 'sleep_time is 0; retries busy-wait' END,
                CASE WHEN i_statement_duration = 0
                    THEN 'statement_duration is 0; no retry after the first failed attempt' END,
                CASE WHEN i_ddl_lock_timeout > i_statement_duration
                    THEN 'ddl_lock_timeout exceeds statement_duration; one attempt can outlast the retry budget' END
                ]::text[],
            NULL::text);
$$;

COMMENT ON FUNCTION ddl_utils.lock_settings_warnings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) IS
    'Returns human-readable warnings for a set of lock timings. Zero values '
        'keep their documented meanings; the warnings only explain the '
        'consequences and never reject the settings.';

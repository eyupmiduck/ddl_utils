CREATE TYPE ddl_utils.lock_settings AS (
    ddl_lock_timeout integer,
    sleep_time integer,
    statement_duration integer
);

COMMENT ON TYPE ddl_utils.lock_settings IS
    'Effective lock settings resolved for a table: ddl_lock_timeout, '
        'sleep_time and statement_duration, all in milliseconds.';

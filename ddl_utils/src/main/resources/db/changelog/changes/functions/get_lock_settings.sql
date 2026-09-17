CREATE OR REPLACE FUNCTION ddl_utils.get_lock_settings()
    RETURNS TABLE (
        ddl_lock_timeout   integer,
        sleep_time         integer,
        statement_duration integer
    )
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
BEGIN
    RETURN QUERY
        SELECT ls.ddl_lock_timeout, ls.sleep_time, ls.statement_duration
        FROM ddl_utils.database_lock_settings AS ls
        WHERE ls.id = 1;
END;
$$;

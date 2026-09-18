CREATE OR REPLACE FUNCTION ddl_utils.get_lock_settings(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text
)
    RETURNS TABLE
            (
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
    -- Each RETURN QUERY sets FOUND based on whether it returned rows, so the
    -- chain falls through table -> schema -> database. The projections
    -- deliberately repeat the accessor lookups: keeping the cascade in one
    -- routine is clearer than composing three set-returning functions.
    RETURN QUERY
        SELECT tls.ddl_lock_timeout, tls.sleep_time, tls.statement_duration
        FROM ddl_utils.table_lock_settings AS tls
        WHERE tls.schema_name = i_schema_name
          AND tls.table_name = i_table_name;

    IF FOUND THEN
        RETURN;
    END IF;

    RETURN QUERY
        SELECT sls.ddl_lock_timeout, sls.sleep_time, sls.statement_duration
        FROM ddl_utils.schema_lock_settings AS sls
        WHERE sls.schema_name = i_schema_name;

    IF FOUND THEN
        RETURN;
    END IF;

    RETURN QUERY
        SELECT dls.ddl_lock_timeout, dls.sleep_time, dls.statement_duration
        FROM ddl_utils.database_lock_settings AS dls
        WHERE dls.id = 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'ddl_utils.get_lock_settings: no lock settings found for schema % and table % and no database defaults (id = 1)',
            i_schema_name, i_table_name
            USING ERRCODE = 'P0002';
    END IF;
END;
$$;

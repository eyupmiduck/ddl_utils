CREATE OR REPLACE FUNCTION ddl_utils.set_schema_lock_settings(
    i_schema_name ddl_utils.non_null_text,
    i_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_sleep_time ddl_utils.non_negative_integer,
    i_statement_duration ddl_utils.non_negative_integer
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS
$$
BEGIN
    -- The schema name is stored as given and not resolved against
    -- pg_namespace, so settings can be provisioned before the schema exists.
    -- A typo simply never matches.
    --
    -- Concurrent upserts for the same schema can block on the primary-key
    -- conflict and can surface a serialization failure at REPEATABLE READ or
    -- SERIALIZABLE; callers that must survive that retry.
    --
    -- The name is stored verbatim, so warn when it does not resolve: the
    -- settings would be stored but never match a lookup.
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = i_schema_name) THEN
        RAISE WARNING
            'ddl_utils.set_schema_lock_settings: schema "%" does not exist; the settings are stored but will not apply',
            i_schema_name;
    END IF;

    INSERT INTO ddl_utils.schema_lock_settings (schema_name, ddl_lock_timeout, sleep_time, statement_duration)
    VALUES (i_schema_name, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)
    ON CONFLICT (schema_name) DO UPDATE
        SET ddl_lock_timeout   = EXCLUDED.ddl_lock_timeout,
            sleep_time         = EXCLUDED.sleep_time,
            statement_duration = EXCLUDED.statement_duration;
END;
$$;

COMMENT ON FUNCTION ddl_utils.set_schema_lock_settings IS
    'Upserts the lock settings for a schema.';

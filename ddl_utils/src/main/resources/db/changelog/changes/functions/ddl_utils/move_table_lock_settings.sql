CREATE OR REPLACE FUNCTION ddl_utils.move_table_lock_settings(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_new_table_name ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS
$$
DECLARE
    l_source_exists      boolean;
    l_destination_exists boolean;
BEGIN
    -- The two existence checks only decide whether the replacement NOTICE is
    -- accurate. They are best-effort: a concurrent change after them cannot
    -- make the statement below wrong, only the notice stale.
    l_source_exists := EXISTS (SELECT 1
                               FROM ddl_utils.table_lock_settings
                               WHERE schema_name = i_schema_name
                                 AND table_name = i_table_name);
    l_destination_exists := EXISTS (SELECT 1
                                    FROM ddl_utils.table_lock_settings
                                    WHERE schema_name = i_schema_name
                                      AND table_name = i_new_table_name);

    -- A missing source row makes the move a no-op, so a pre-existing
    -- destination row is not replaced and must not be reported as replaced.
    IF l_source_exists
        AND l_destination_exists
        AND i_table_name <> i_new_table_name THEN
        RAISE NOTICE
            'ddl_utils.move_table_lock_settings: replacing the existing lock settings for %.%',
            i_schema_name, i_new_table_name;
    END IF;

    -- One statement: delete the source row and upsert its values under the new
    -- name. There is no read-then-delete window in which a concurrent
    -- set_table_lock_settings on the old name could be silently lost. When the
    -- source has no row nothing is inserted, so a destination row left by a
    -- pre-provisioned override or an earlier rename is untouched.
    --
    -- created_at travels with the moved row; a destination row replaced in
    -- place keeps its own created_at because the conflict branch does not
    -- touch it (the row for that name already existed).
    WITH moved AS (
        DELETE FROM ddl_utils.table_lock_settings
            WHERE schema_name = i_schema_name
                AND table_name = i_table_name
            RETURNING ddl_lock_timeout, sleep_time, statement_duration, created_at)
    INSERT
    INTO ddl_utils.table_lock_settings (schema_name, table_name, ddl_lock_timeout,
                                        sleep_time, statement_duration, created_at)
    SELECT i_schema_name,
           i_new_table_name,
           m.ddl_lock_timeout,
           m.sleep_time,
           m.statement_duration,
           m.created_at
    FROM moved AS m
    ON CONFLICT (schema_name, table_name) DO UPDATE
        SET ddl_lock_timeout   = EXCLUDED.ddl_lock_timeout,
            sleep_time         = EXCLUDED.sleep_time,
            statement_duration = EXCLUDED.statement_duration;
END;
$$;

COMMENT ON FUNCTION ddl_utils.move_table_lock_settings IS
    'Moves a table''s lock settings to a new table name in one statement. A '
        'missing source row makes the move a no-op (nothing is inserted or '
        'replaced); an existing destination row is replaced by the moved values '
        '(keeping its own created_at) and a NOTICE reports the replacement.';

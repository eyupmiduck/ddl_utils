CREATE OR REPLACE FUNCTION ddl_utils.prune_orphaned_lock_settings(
    i_dry_run ddl_utils.non_null_boolean DEFAULT true,
    i_include_missing_schemas ddl_utils.non_null_boolean DEFAULT false
)
    RETURNS TABLE
            (
                level              text,
                schema_name        text,
                table_name         text,
                ddl_lock_timeout   integer,
                sleep_time         integer,
                statement_duration integer,
                action             text
            )
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS
$$
BEGIN
    -- Dry run is the default: an absent relation may be intentional
    -- pre-provisioning, so deletion requires an explicit i_dry_run => false.
    IF i_dry_run::boolean THEN
        RETURN QUERY
            SELECT o.level,
                   o.schema_name,
                   o.table_name,
                   o.ddl_lock_timeout,
                   o.sleep_time,
                   o.statement_duration,
                   'would_delete'::text AS action
            FROM ddl_utils.lock_settings_orphans AS o
            WHERE (o.level = 'table'
                AND (i_include_missing_schemas::boolean OR o.schema_exists))
               OR (o.level = 'schema' AND i_include_missing_schemas::boolean)
            ORDER BY o.level, o.schema_name, o.table_name;
        RETURN;
    END IF;

    RETURN QUERY
        WITH candidates AS (
            SELECT o.level, o.schema_name, o.table_name
            FROM ddl_utils.lock_settings_orphans AS o
            WHERE (o.level = 'table'
                AND (i_include_missing_schemas::boolean OR o.schema_exists))
               OR (o.level = 'schema' AND i_include_missing_schemas::boolean)
        ),
             deleted_tables AS (
                 DELETE FROM ddl_utils.table_lock_settings AS t
                     USING candidates AS c
                     WHERE c.level = 'table'
                         AND t.schema_name = c.schema_name
                         AND t.table_name = c.table_name
                     RETURNING t.schema_name, t.table_name, t.ddl_lock_timeout,
                         t.sleep_time, t.statement_duration
             ),
             deleted_schemas AS (
                 DELETE FROM ddl_utils.schema_lock_settings AS s
                     USING candidates AS c
                     WHERE c.level = 'schema'
                         AND s.schema_name = c.schema_name
                     RETURNING s.schema_name, s.ddl_lock_timeout, s.sleep_time,
                         s.statement_duration
             )
        SELECT 'table'::text AS level,
               dt.schema_name,
               dt.table_name,
               dt.ddl_lock_timeout,
               dt.sleep_time,
               dt.statement_duration,
               'deleted'::text AS action
        FROM deleted_tables AS dt
        UNION ALL
        SELECT 'schema'::text,
               ds.schema_name,
               NULL::text,
               ds.ddl_lock_timeout,
               ds.sleep_time,
               ds.statement_duration,
               'deleted'::text
        FROM deleted_schemas AS ds
        ORDER BY 1, 2, 3;
END;
$$;

COMMENT ON FUNCTION ddl_utils.prune_orphaned_lock_settings(
    ddl_utils.non_null_boolean,
    ddl_utils.non_null_boolean
    ) IS
    'Reports or deletes lock-settings rows that no longer point at an ordinary/'
        'partitioned table. Defaults to a dry run that deletes nothing; deleting '
        'requires i_dry_run => false. Missing-schema table/schema rows are kept '
        'unless i_include_missing_schemas => true, because absence may be '
        'intentional pre-provisioning.';

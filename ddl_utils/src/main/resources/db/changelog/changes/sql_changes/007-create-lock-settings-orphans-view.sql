CREATE VIEW ddl_utils.lock_settings_orphans AS
SELECT
'table'::text AS level,
       t.schema_name,
       t.table_name,
       t.ddl_lock_timeout,
       t.sleep_time,
       t.statement_duration,
       (n.oid IS NOT NULL) AS schema_exists,
       (c.oid IS NOT NULL) AS relation_exists,
       c.relkind::text AS relation_kind,
       t.created_at,
       t.updated_at
FROM ddl_utils.table_lock_settings AS t
         LEFT JOIN pg_catalog.pg_namespace AS n ON t.schema_name = n.nspname
         LEFT JOIN pg_catalog.pg_class AS c
                   ON n.oid = c.relnamespace AND t.table_name = c.relname
WHERE c.oid IS NULL
   OR c.relkind NOT IN ('r', 'p')
UNION ALL
SELECT
'schema'::text,
       s.schema_name,
       NULL::text,
       s.ddl_lock_timeout,
       s.sleep_time,
       s.statement_duration,
       (n.oid IS NOT NULL),
       NULL::boolean,
       NULL::text,
       s.created_at,
       s.updated_at
FROM ddl_utils.schema_lock_settings AS s
         LEFT JOIN pg_catalog.pg_namespace AS n ON s.schema_name = n.nspname
WHERE n.oid IS NULL;

COMMENT ON VIEW ddl_utils.lock_settings_orphans IS
    'Lock-settings rows that do not currently point at an ordinary/partitioned '
        'table: table settings with no such relation (or a relation of another '
        'kind), and schema settings with no such schema. Absence can be '
        'intentional pre-provisioning; use explain_lock_settings and a dry-run '
        'prune before deleting.';

COMMENT ON COLUMN ddl_utils.lock_settings_orphans.level IS
    'Setting level: table or schema.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.schema_name IS
    'Schema key stored in the settings row.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.table_name IS
    'Table key for a table row; NULL for a schema row.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.ddl_lock_timeout IS
    'Per-attempt lock_timeout in milliseconds.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.sleep_time IS
    'Milliseconds between retry attempts.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.statement_duration IS
    'Lock-acquisition retry budget in milliseconds.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.schema_exists IS
    'Whether pg_namespace has the schema key.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.relation_exists IS
    'Whether pg_class has the relation key; true only for a non-table relation '
        'such as a view or sequence, false when no relation matches, NULL for a '
        'schema row.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.relation_kind IS
    'pg_class.relkind when a relation matches, otherwise NULL.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.created_at IS
    'Settings row creation time.';
COMMENT ON COLUMN ddl_utils.lock_settings_orphans.updated_at IS
    'Settings row last update time.';

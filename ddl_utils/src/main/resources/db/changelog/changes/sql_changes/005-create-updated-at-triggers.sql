-- Every table gets created_at/updated_at (timestamptz, defaulted to now()), and
-- ddl_utils.set_updated_at() keeps updated_at current on every UPDATE. The
-- trigger is attached once per table here; it lives in the table's own schema.
-- PostgreSQL has no CREATE TRIGGER IF NOT EXISTS, so drop first to keep the
-- changeset re-runnable against a partially seeded database.
DROP TRIGGER IF EXISTS database_lock_settings_set_updated_at ON ddl_utils.database_lock_settings;
CREATE TRIGGER database_lock_settings_set_updated_at
    BEFORE UPDATE
    ON ddl_utils.database_lock_settings
    FOR EACH ROW
EXECUTE FUNCTION ddl_utils.set_updated_at();

DROP TRIGGER IF EXISTS schema_lock_settings_set_updated_at ON ddl_utils.schema_lock_settings;
CREATE TRIGGER schema_lock_settings_set_updated_at
    BEFORE UPDATE
    ON ddl_utils.schema_lock_settings
    FOR EACH ROW
EXECUTE FUNCTION ddl_utils.set_updated_at();

DROP TRIGGER IF EXISTS table_lock_settings_set_updated_at ON ddl_utils.table_lock_settings;
CREATE TRIGGER table_lock_settings_set_updated_at
    BEFORE UPDATE
    ON ddl_utils.table_lock_settings
    FOR EACH ROW
EXECUTE FUNCTION ddl_utils.set_updated_at();

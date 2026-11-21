-- The schema is owned by the role that runs the migration (ddl_utils_owner in
-- the documented setup), so Liquibase must connect as that role: replacing a
-- routine requires the migration role to own its schema.
CREATE SCHEMA IF NOT EXISTS ddl_utils_lib;

COMMENT ON SCHEMA ddl_utils_lib IS
    'Generic DDL helpers that take the lock settings explicitly.';

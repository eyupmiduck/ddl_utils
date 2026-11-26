-- Rollback of the caller usage for the identifier domain.
REVOKE USAGE ON DOMAIN ddl_utils.identifier FROM ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.identifier TO public;

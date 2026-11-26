-- Caller usage for the identifier domain. The domain is used by the resolver
-- paths; the caller needs USAGE on it to evaluate the cast.
GRANT USAGE ON DOMAIN ddl_utils.identifier TO ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.identifier FROM public;

REVOKE USAGE ON DOMAIN ddl_utils.non_null_text FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_negative_integer FROM ddl_utils_caller;
REVOKE SELECT, INSERT, UPDATE, DELETE ON ddl_utils.example FROM ddl_utils_caller;
REVOKE USAGE ON SCHEMA ddl_utils FROM ddl_utils_caller;

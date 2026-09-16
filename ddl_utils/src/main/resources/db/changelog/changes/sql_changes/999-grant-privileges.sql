GRANT USAGE ON SCHEMA ddl_utils TO ddl_utils_caller;
GRANT SELECT, INSERT, UPDATE, DELETE ON ddl_utils.example TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_negative_integer TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_null_text TO ddl_utils_caller;

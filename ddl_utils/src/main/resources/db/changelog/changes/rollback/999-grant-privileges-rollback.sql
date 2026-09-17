REVOKE EXECUTE ON FUNCTION ddl_utils.set_schema_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_schema_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_schema_lock_settings(
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.get_schema_lock_settings(
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_lock_settings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_lock_settings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_lock_settings() FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.get_lock_settings() TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_text_array,
    ddl_utils.non_empty_non_null_boolean_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.add_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_text_array,
    ddl_utils.non_empty_non_null_boolean_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    text,
    ddl_utils.non_null_boolean,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    text,
    ddl_utils.non_null_boolean,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE USAGE ON DOMAIN ddl_utils.non_empty_non_null_integer_array FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_empty_non_null_boolean_array FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_empty_non_null_text_array FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_empty_text_array FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_null_boolean FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_null_text FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_negative_integer FROM ddl_utils_caller;
REVOKE SELECT ON ddl_utils.schema_lock_settings FROM ddl_utils_caller;
REVOKE SELECT ON ddl_utils.database_lock_settings FROM ddl_utils_caller;
REVOKE SELECT, INSERT, UPDATE, DELETE ON ddl_utils.example FROM ddl_utils_caller;
REVOKE USAGE ON SCHEMA ddl_utils FROM ddl_utils_caller;

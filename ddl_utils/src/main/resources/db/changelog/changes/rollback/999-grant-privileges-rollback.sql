-- Rollback of the caller grants: revoke what the forward changeset granted and
-- restore PostgreSQL's default PUBLIC execute on each routine.
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_text_array,
    ddl_utils.non_empty_non_null_boolean_array
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.add_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_text_array,
    ddl_utils.non_empty_non_null_boolean_array
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.clear_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.clear_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.get_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.get_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.clear_schema_lock_settings(
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.clear_schema_lock_settings(
    ddl_utils.non_null_text
    ) TO public;
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
REVOKE EXECUTE ON FUNCTION ddl_utils.set_database_lock_settings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.set_database_lock_settings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_database_lock_settings() FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.get_database_lock_settings() TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.has_top_level_comma(
    text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.has_top_level_comma(
    text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_columns(
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
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_columns(
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
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE USAGE ON DOMAIN ddl_utils.non_empty_non_null_boolean_array FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_empty_non_null_text_array FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_empty_text_array FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_null_boolean FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_null_text FROM ddl_utils_caller;
REVOKE USAGE ON DOMAIN ddl_utils.non_negative_integer FROM ddl_utils_caller;
REVOKE SELECT ON ddl_utils.table_lock_settings FROM ddl_utils_caller;
REVOKE SELECT ON ddl_utils.schema_lock_settings FROM ddl_utils_caller;
REVOKE SELECT ON ddl_utils.database_lock_settings FROM ddl_utils_caller;
REVOKE USAGE ON SCHEMA ddl_utils_lib FROM ddl_utils_caller;
REVOKE USAGE ON SCHEMA ddl_utils FROM ddl_utils_caller;

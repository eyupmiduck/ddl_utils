GRANT USAGE ON SCHEMA ddl_utils TO ddl_utils_caller;
GRANT USAGE ON SCHEMA ddl_utils_lib TO ddl_utils_caller;
GRANT SELECT ON ddl_utils.database_lock_settings TO ddl_utils_caller;
GRANT SELECT ON ddl_utils.schema_lock_settings TO ddl_utils_caller;
GRANT SELECT ON ddl_utils.table_lock_settings TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_negative_integer TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_null_text TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_null_boolean TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_empty_text_array TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_empty_non_null_text_array TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_empty_non_null_boolean_array TO ddl_utils_caller;

-- Functions grant EXECUTE to PUBLIC by default; revoke it and grant only to
-- the caller role, so execution is explicit.
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
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
    ) FROM public;
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
    ) TO ddl_utils_caller;
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
    ) FROM public;
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
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.has_top_level_comma(
    text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.has_top_level_comma(
    text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_database_lock_settings() FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.get_database_lock_settings() TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_database_lock_settings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_database_lock_settings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_schema_lock_settings(
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.get_schema_lock_settings(
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_schema_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_schema_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.clear_schema_lock_settings(
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.clear_schema_lock_settings(
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.get_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.clear_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.clear_table_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.get_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.get_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_text_array,
    ddl_utils.non_empty_non_null_boolean_array
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.add_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_empty_text_array,
    ddl_utils.non_empty_non_null_boolean_array
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.add_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.rename_column(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_columns(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_column_default(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_statistics(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_storage(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_column_compression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_expression(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_check_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_check_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_foreign_key(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_foreign_key(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.validate_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.validate_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.rename_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.rename_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_primary_key_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_primary_key_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_unique_constraint_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_unique_constraint_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.rename_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.rename_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_table_storage_parameter(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_table_storage_parameter(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.add_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_identity(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_boolean
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_check_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.add_check_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_foreign_key(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.add_foreign_key(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_empty_non_null_text_array
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.validate_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.validate_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.rename_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.rename_constraint(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_primary_key_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.add_primary_key_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_unique_constraint_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.add_unique_constraint_using_index(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.rename_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.rename_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_table_storage_parameter(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_table_storage_parameter(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.set_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.set_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;
REVOKE EXECUTE ON FUNCTION ddl_utils.set_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.set_not_null(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;

-- Rollback of the caller grants for the idempotent convergence helpers.
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.column_exists(
    regclass,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.column_exists(
    regclass,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_column_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_column_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_column_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_column_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.drop_constraint_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.drop_constraint_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.drop_constraint_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.drop_constraint_if_exists(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_primary_key_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_primary_key_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_primary_key_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.add_primary_key_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils_lib.add_unique_constraint_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils_lib.add_unique_constraint_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;
REVOKE EXECUTE ON FUNCTION ddl_utils.add_unique_constraint_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.add_unique_constraint_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;

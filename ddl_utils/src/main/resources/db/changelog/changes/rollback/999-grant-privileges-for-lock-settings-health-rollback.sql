-- Rollback of the lock-settings health grants.
REVOKE SELECT ON ddl_utils.lock_settings_orphans FROM ddl_utils_caller;

REVOKE EXECUTE ON FUNCTION ddl_utils.lock_settings_warnings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.lock_settings_warnings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO public;

REVOKE EXECUTE ON FUNCTION ddl_utils.explain_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.explain_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO public;

REVOKE EXECUTE ON FUNCTION ddl_utils.prune_orphaned_lock_settings(
    ddl_utils.non_null_boolean,
    ddl_utils.non_null_boolean
    ) FROM ddl_utils_caller;
GRANT EXECUTE ON FUNCTION ddl_utils.prune_orphaned_lock_settings(
    ddl_utils.non_null_boolean,
    ddl_utils.non_null_boolean
    ) TO public;

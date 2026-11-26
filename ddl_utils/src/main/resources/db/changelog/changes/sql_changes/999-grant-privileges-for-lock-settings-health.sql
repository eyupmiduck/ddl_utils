-- Caller access for the lock-settings health view and helpers. This is a
-- separate changeset because 999-grant-privileges is frozen.
REVOKE ALL ON ddl_utils.lock_settings_orphans FROM public;
GRANT SELECT ON ddl_utils.lock_settings_orphans TO ddl_utils_caller;

REVOKE EXECUTE ON FUNCTION ddl_utils.lock_settings_warnings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.lock_settings_warnings(
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) TO ddl_utils_caller;

REVOKE EXECUTE ON FUNCTION ddl_utils.explain_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.explain_lock_settings(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) TO ddl_utils_caller;

REVOKE EXECUTE ON FUNCTION ddl_utils.prune_orphaned_lock_settings(
    ddl_utils.non_null_boolean,
    ddl_utils.non_null_boolean
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.prune_orphaned_lock_settings(
    ddl_utils.non_null_boolean,
    ddl_utils.non_null_boolean
    ) TO ddl_utils_caller;

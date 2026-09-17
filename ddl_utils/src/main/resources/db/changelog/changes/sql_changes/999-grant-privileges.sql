GRANT USAGE ON SCHEMA ddl_utils TO ddl_utils_caller;
GRANT SELECT, INSERT, UPDATE, DELETE ON ddl_utils.example TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_negative_integer TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_null_text TO ddl_utils_caller;
GRANT USAGE ON DOMAIN ddl_utils.non_null_boolean TO ddl_utils_caller;

-- Functions grant EXECUTE to PUBLIC by default; revoke it and grant only to
-- the caller role, so execution is explicit.
REVOKE EXECUTE ON FUNCTION ddl_utils.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) FROM public;
GRANT EXECUTE ON FUNCTION ddl_utils.alter_table(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
) TO ddl_utils_caller;
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
) FROM public;
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
) TO ddl_utils_caller;

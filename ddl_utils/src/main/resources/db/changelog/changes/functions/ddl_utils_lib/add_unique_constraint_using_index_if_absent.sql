CREATE OR REPLACE FUNCTION ddl_utils_lib.add_unique_constraint_using_index_if_absent(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_index_name ddl_utils.non_null_text,
    i_ddl_lock_timeout ddl_utils.non_negative_integer,
    i_sleep_time ddl_utils.non_negative_integer,
    i_statement_duration ddl_utils.non_negative_integer
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_relation            regclass;
    l_index               regclass;
    l_contype             text;
    l_existing_definition text;
    l_backing_constraint  text;
BEGIN
    PERFORM ddl_utils_lib.assert_identifier_length(
            i_value => i_constraint_name,
            i_context => 'ddl_utils_lib.add_unique_constraint_using_index_if_absent',
            i_argument => 'i_constraint_name');

    l_relation := ddl_utils_lib.resolve_table_relation(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_context => 'ddl_utils_lib.add_unique_constraint_using_index_if_absent',
            i_relation_label => 'table');

    -- Serialize the check-and-attach sequence on the table and index. A
    -- different kind of constraint on the same index shares this lock too.
    PERFORM ddl_utils_lib.acquire_operation_lock(
            i_context => 'ddl_utils_lib.add_constraint_using_index_if_absent',
            i_keys => ARRAY [
                pg_catalog.format('%I.%I', i_schema_name, i_table_name),
                pg_catalog.format('%I', i_index_name)
                ]);

    -- The constraint name is the identity. USING INDEX renames the index to
    -- the constraint name, so the original index name is gone after the first
    -- attach; a same-named constraint of the expected kind is therefore
    -- treated as converged without re-resolving the index.
    SELECT c.contype::text, pg_catalog.pg_get_constraintdef(c.oid)
    INTO l_contype, l_existing_definition
    FROM pg_catalog.pg_constraint AS c
    WHERE c.conrelid = l_relation
      AND c.conname = i_constraint_name;

    IF FOUND THEN
        IF l_contype <> 'u' THEN
            RAISE EXCEPTION
                'ddl_utils_lib.add_unique_constraint_using_index_if_absent: constraint % already exists on %.% with a different definition (existing: %)',
                i_constraint_name, i_schema_name, i_table_name, l_existing_definition
                USING ERRCODE = '42710';
        END IF;
        RETURN;
    END IF;

    PERFORM ddl_utils_lib.validate_unique_index_for_attach(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_index_name => i_index_name,
            i_context => 'ddl_utils_lib.add_unique_constraint_using_index_if_absent');

    l_index := pg_catalog.to_regclass(
            pg_catalog.format('%I.%I', i_schema_name, i_index_name));
    IF l_index IS NULL THEN
        RAISE EXCEPTION
            'ddl_utils_lib.add_unique_constraint_using_index_if_absent: index %.% does not exist',
            i_schema_name, i_index_name
            USING ERRCODE = '42704';
    END IF;

    -- A unique index can back only one constraint. Name the existing owner
    -- instead of letting ADD CONSTRAINT fail with a generic duplicate error.
    SELECT c.conname::text
    INTO l_backing_constraint
    FROM pg_catalog.pg_constraint AS c
    WHERE c.conrelid = l_relation
      AND c.conindid = l_index::oid
      AND c.contype IN ('p', 'u');

    IF FOUND THEN
        RAISE EXCEPTION
            'ddl_utils_lib.add_unique_constraint_using_index_if_absent: index %.% already backs constraint %',
            i_schema_name, i_index_name, l_backing_constraint
            USING ERRCODE = '42710';
    END IF;

    PERFORM ddl_utils_lib.add_unique_constraint_using_index(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name,
            i_constraint_name => i_constraint_name,
            i_index_name => i_index_name,
            i_ddl_lock_timeout => i_ddl_lock_timeout,
            i_sleep_time => i_sleep_time,
            i_statement_duration => i_statement_duration
            );
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.add_unique_constraint_using_index_if_absent(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer,
    ddl_utils.non_negative_integer
    ) IS
    'Attaches a pre-built unique index as a unique constraint when the '
        'constraint is absent; a same-named unique constraint is a no-op because '
        'the first attach renames the index to the constraint name. A '
        'same-named constraint of another kind, or an index already backing '
        'another constraint, raises 42710.';

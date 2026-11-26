CREATE OR REPLACE FUNCTION ddl_utils_lib.resolve_table_relation(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_context ddl_utils.non_null_text,
    i_relation_label ddl_utils.non_null_text
)
    RETURNS regclass
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
DECLARE
    l_relation regclass;
    l_relkind  text;
BEGIN
    -- Resolve without a ::regclass cast, so a missing relation is a controlled
    -- 42P01 instead of a raw "relation does not exist" cast error. The names are
    -- cast to the identifier domain first: PostgreSQL would otherwise truncate
    -- an over-long identifier and resolve a different relation.
    l_relation := pg_catalog.to_regclass(
            pg_catalog.format('%I.%I',
                              i_schema_name::ddl_utils.identifier,
                              i_table_name::ddl_utils.identifier));

    SELECT c.relkind::text
    INTO l_relkind
    FROM pg_catalog.pg_class AS c
    WHERE c.oid = l_relation;

    IF l_relkind IS NULL THEN
        RAISE EXCEPTION '%: % %.% does not exist',
            i_context, i_relation_label, i_schema_name, i_table_name
            USING ERRCODE = '42P01';
    END IF;

    -- A named relation with an attribute is not necessarily a table a CHECK
    -- constraint and SET NOT NULL can be added to; reject views, sequences, ...
    -- A partitioned parent ('p') is accepted: the committed procedures recurse
    -- to its partitions, and the per-partition locks are the caller's concern.
    IF l_relkind NOT IN ('r', 'p') THEN
        RAISE EXCEPTION '%: %.% is not a table',
            i_context, i_schema_name, i_table_name
            USING ERRCODE = '42809';
    END IF;

    RETURN l_relation;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.resolve_table_relation(
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text,
    ddl_utils.non_null_text
    ) IS
    'Resolves schema.table to its regclass and requires relkind r/p, raising '
        '42P01 (missing) or 42809 (not a table) with the context and label. '
        'Names longer than 63 bytes raise 23514 through ddl_utils.identifier '
        'instead of being truncated.';

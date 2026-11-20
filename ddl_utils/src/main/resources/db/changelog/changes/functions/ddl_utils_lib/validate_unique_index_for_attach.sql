CREATE OR REPLACE FUNCTION ddl_utils_lib.validate_unique_index_for_attach(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_index_name ddl_utils.non_null_text,
    i_context ddl_utils.non_null_text
)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY INVOKER
AS
$$
DECLARE
    l_table_oid   regclass;
    l_index_oid   regclass;
    l_index_table regclass;
    l_relkind     text;
BEGIN
    -- The index name is emitted as a single %I identifier and resolved in the
    -- table's schema; reject a qualified or pre-quoted name before resolving it.
    -- parse_ident raises its own 42601 for unparseable input, so map that to the
    -- same 22023 diagnostic.
    BEGIN
        IF pg_catalog.cardinality(pg_catalog.parse_ident(i_index_name)) <> 1
            OR pg_catalog.strpos(i_index_name, '"') > 0 THEN
            RAISE EXCEPTION
                '%: i_index_name must be an unquoted, unqualified identifier',
                i_context
                USING ERRCODE = '22023';
        END IF;
    EXCEPTION
        WHEN syntax_error THEN
            RAISE EXCEPTION
                '%: i_index_name must be an unquoted, unqualified identifier',
                i_context
                USING ERRCODE = '22023';
    END;

    IF pg_catalog.octet_length(i_index_name) > 63 THEN
        RAISE EXCEPTION '%: the index name must be at most 63 bytes', i_context
            USING ERRCODE = '22023';
    END IF;

    -- Resolve the table without a ::regclass cast, so a missing table is a
    -- controlled 42P01 rather than a raw "relation does not exist" cast error.
    l_table_oid := pg_catalog.to_regclass(
            pg_catalog.format('%I.%I', i_schema_name, i_table_name));

    SELECT c.relkind::text
    INTO l_relkind
    FROM pg_catalog.pg_class AS c
    WHERE c.oid = l_table_oid;

    IF l_relkind IS NULL THEN
        RAISE EXCEPTION '%: table %.% does not exist', i_context, i_schema_name, i_table_name
            USING ERRCODE = '42P01';
    END IF;

    IF l_relkind NOT IN ('r', 'p') THEN
        RAISE EXCEPTION '%: %.% is not a table', i_context, i_schema_name, i_table_name
            USING ERRCODE = '42809';
    END IF;

    l_index_oid := pg_catalog.to_regclass(
            pg_catalog.format('%I.%I', i_schema_name, i_index_name));

    SELECT c.relkind::text
    INTO l_relkind
    FROM pg_catalog.pg_class AS c
    WHERE c.oid = l_index_oid;

    IF l_relkind IS NULL THEN
        RAISE EXCEPTION '%: index %.% does not exist', i_context, i_schema_name, i_index_name
            USING ERRCODE = '42704';
    END IF;

    -- PostgreSQL does not support ALTER TABLE ... USING INDEX on a partitioned
    -- table, so report that rather than the misleading "index does not exist".
    IF l_relkind = 'I' THEN
        RAISE EXCEPTION '%: partitioned index %.% is not supported',
            i_context, i_schema_name, i_index_name
            USING ERRCODE = '0A000';
    END IF;

    IF l_relkind <> 'i' THEN
        RAISE EXCEPTION '%: %.% is not an index', i_context, i_schema_name, i_index_name
            USING ERRCODE = '42809';
    END IF;

    SELECT ix.indrelid::regclass
    INTO l_index_table
    FROM pg_catalog.pg_index AS ix
    WHERE ix.indexrelid = l_index_oid;

    IF l_index_table IS DISTINCT FROM l_table_oid THEN
        RAISE EXCEPTION '%: index %.% is not on table %.%',
            i_context, i_schema_name, i_index_name, i_schema_name, i_table_name
            USING ERRCODE = '42809';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM pg_catalog.pg_index AS ix
                   WHERE ix.indexrelid = l_index_oid
                     AND ix.indisunique
                     AND ix.indisvalid
                     AND ix.indpred IS NULL
                     AND ix.indexprs IS NULL) THEN
        RAISE EXCEPTION
            '%: index %.% is not a valid, non-partial, non-expression unique index on %.%',
            i_context, i_schema_name, i_index_name, i_schema_name, i_table_name
            USING ERRCODE = '42809';
    END IF;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.validate_unique_index_for_attach IS
    'Validates that an unqualified index name resolves to a usable unique index '
        'on the table, raising a controlled 42P01/42704/0A000/42809 otherwise. '
        'Used by the USING INDEX helpers.';

CREATE OR REPLACE FUNCTION ddl_utils_lib.foreign_key_definition_matches(
    i_schema_name ddl_utils.non_null_text,
    i_table_name ddl_utils.non_null_text,
    i_constraint_name ddl_utils.non_null_text,
    i_referenced_schema_name ddl_utils.non_null_text,
    i_referenced_table_name ddl_utils.non_null_text,
    i_column_names ddl_utils.non_empty_non_null_text_array,
    i_referenced_column_names ddl_utils.non_empty_non_null_text_array
)
    RETURNS boolean
    LANGUAGE plpgsql
    STABLE
    SECURITY INVOKER
AS
$$
DECLARE
    l_relation            regclass;
    l_referenced_relation regclass;
    l_matches             boolean;
BEGIN
    l_relation := pg_catalog.to_regclass(
            pg_catalog.format('%I.%I', i_schema_name, i_table_name));
    l_referenced_relation := pg_catalog.to_regclass(
            pg_catalog.format('%I.%I', i_referenced_schema_name, i_referenced_table_name));

    IF l_relation IS NULL OR l_referenced_relation IS NULL THEN
        RETURN false;
    END IF;

    -- Compare the stored definition with the request: same referenced table,
    -- PostgreSQL-default actions/match/deferrability, and the same source ->
    -- referenced column mapping as a multiset (PostgreSQL may reorder the keys
    -- to align them with the referenced unique index).
    l_matches := EXISTS (SELECT 1
                         FROM pg_catalog.pg_constraint AS c
                         WHERE c.conrelid = l_relation
                           AND c.conname = i_constraint_name
                           AND c.contype = 'f'
                           AND c.confrelid = l_referenced_relation
                           AND c.confupdtype = 'a'
                           AND c.confdeltype = 'a'
                           AND c.confmatchtype = 's'
                           AND NOT c.condeferrable
                           AND NOT c.condeferred
                           AND NOT EXISTS (WITH stored AS (SELECT sa.attname AS source_name,
                                                                  ra.attname AS referenced_name
                                                           FROM pg_catalog.generate_subscripts(c.conkey, 1) AS s(i)
                                                                    JOIN pg_catalog.pg_attribute AS sa
                                                                         ON sa.attrelid = c.conrelid
                                                                             AND sa.attnum = c.conkey[s.i]
                                                                    JOIN pg_catalog.pg_attribute AS ra
                                                                         ON ra.attrelid = c.confrelid
                                                                             AND ra.attnum = c.confkey[s.i]),
                                                requested AS (SELECT s.name AS source_name,
                                                                     r.name AS referenced_name
                                                              FROM pg_catalog.unnest(i_column_names::text[])
                                                                       WITH ORDINALITY AS s(name, ord)
                                                                       JOIN pg_catalog.unnest(
                                                                      i_referenced_column_names::text[]
                                                                            ) WITH ORDINALITY AS r(name, ord)
                                                                            ON s.ord = r.ord)
                                           SELECT 1
                                           WHERE EXISTS (SELECT source_name, referenced_name
                                                         FROM requested
                                                         EXCEPT ALL
                                                         SELECT source_name, referenced_name
                                                         FROM stored)
                                              OR EXISTS (SELECT source_name, referenced_name
                                                         FROM stored
                                                         EXCEPT ALL
                                                         SELECT source_name, referenced_name
                                                         FROM requested)));

    RETURN l_matches;
END;
$$;

COMMENT ON FUNCTION ddl_utils_lib.foreign_key_definition_matches IS
    'Returns whether a foreign key matches the requested referenced relation and '
        'source -> referenced column mapping, with PostgreSQL''s default actions, '
        'match type and deferrability.';

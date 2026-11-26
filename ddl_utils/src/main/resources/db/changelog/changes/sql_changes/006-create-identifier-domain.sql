CREATE DOMAIN ddl_utils.identifier AS text
    CONSTRAINT identifier_check CHECK (
        value IS NOT NULL
        AND pg_catalog.btrim(value, E' \t\n\r\f\013') <> ''
        AND pg_catalog.octet_length(value) <= 63
        );

COMMENT ON DOMAIN ddl_utils.identifier IS
    'text used as a PostgreSQL identifier: NOT NULL, not ASCII-whitespace-only, '
        'and at most 63 bytes (NAMEDATALEN - 1).';

CREATE DOMAIN ddl_utils.non_negative_integer AS integer
    CONSTRAINT non_negative_integer_check CHECK (value IS NOT NULL AND value >= 0);

CREATE DOMAIN ddl_utils.non_null_text AS text
    CONSTRAINT non_null_text_check CHECK (value IS NOT NULL AND pg_catalog.btrim(value) <> '');

CREATE DOMAIN ddl_utils.non_null_boolean AS boolean
    CONSTRAINT non_null_boolean_check CHECK (value IS NOT NULL);

CREATE DOMAIN ddl_utils.non_empty_text_array AS text[]
    CONSTRAINT non_empty_text_array_check CHECK (
        value IS NOT NULL AND pg_catalog.cardinality(value) >= 1
        );

CREATE DOMAIN ddl_utils.non_empty_non_null_text_array AS text[]
    CONSTRAINT non_empty_non_null_text_array_check CHECK (
        value IS NOT NULL
            AND pg_catalog.cardinality(value) >= 1
            AND pg_catalog.array_position(value, NULL) IS NULL
        );

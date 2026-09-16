CREATE DOMAIN ddl_utils.non_negative_integer AS integer
    CONSTRAINT non_negative_integer_check CHECK (value IS NOT NULL AND value >= 0);

CREATE DOMAIN ddl_utils.non_null_text AS text
    CONSTRAINT non_null_text_check CHECK (value IS NOT NULL);

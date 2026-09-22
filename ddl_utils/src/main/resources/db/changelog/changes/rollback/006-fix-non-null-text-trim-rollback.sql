-- Restore the original (buggy) non_null_text check. Kept NOT VALID so the
-- rollback, like the forward change, stays metadata-only.
ALTER DOMAIN ddl_utils.non_null_text
    DROP CONSTRAINT non_null_text_check;

ALTER DOMAIN ddl_utils.non_null_text
    ADD CONSTRAINT non_null_text_check CHECK (
        value IS NOT NULL AND pg_catalog.btrim(value, E' \t\n\r\f\v') <> ''
        ) NOT VALID;

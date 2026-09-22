-- The original non_null_text check trimmed E' \t\n\r\f\v'. In PostgreSQL the
-- \v escape is not vertical tab: it is the literal letter 'v'. So 'vvv' was
-- wrongly rejected as blank while a vertical-tab-only value passed.
--
-- Replace the check with the documented ASCII whitespace set, using the octal
-- escape \013 for vertical tab. ADD CONSTRAINT ... NOT VALID keeps the change
-- metadata-only on existing databases (no validation scan); the corrected
-- predicate is enforced for every new value.
ALTER DOMAIN ddl_utils.non_null_text
    DROP CONSTRAINT non_null_text_check;

ALTER DOMAIN ddl_utils.non_null_text
    ADD CONSTRAINT non_null_text_check CHECK (
        value IS NOT NULL AND pg_catalog.btrim(value, E' \t\n\r\f\013') <> ''
        ) NOT VALID;

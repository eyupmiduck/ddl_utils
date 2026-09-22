CREATE OR REPLACE FUNCTION ddl_utils_lib.has_top_level_comma(
    i_value text
)
    RETURNS boolean
    LANGUAGE plpgsql
    IMMUTABLE
    SECURITY INVOKER
AS
$func$
DECLARE
    -- Dollar-quote tags: the first character may not be a digit.
    l_tag_first_chars constant text := 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_';
    l_tag_chars       constant text := 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_';
    l_length          integer;
    l_pos             integer := 1;
    l_scan            integer;
    l_depth           integer := 0;
    l_block_depth     integer := 0;
    l_state           text    := 'code';
    l_dollar_tag      text;
    l_char            text;
    l_next            text;
    l_prev            text;
BEGIN
    IF i_value IS NULL THEN
        RETURN false;
    END IF;

    -- Single left-to-right scan. Strings, dollar-quoted literals, quoted
    -- identifiers and comments are skipped as opaque text, so a comma inside
    -- them is not a separator. A comma in code at bracket depth 0 is top-level.
    -- Unbalanced delimiters leave l_depth > 0, so any comma they contain is
    -- conservatively treated as nested (the caller's fragment is rejected
    -- downstream when the SQL is invalid).
    l_length := pg_catalog.length(i_value);

    WHILE l_pos <= l_length LOOP
        l_char := pg_catalog.substr(i_value, l_pos, 1);
        l_next := pg_catalog.substr(i_value, l_pos + 1, 1);
        l_prev := pg_catalog.substr(i_value, l_pos - 1, 1);

        IF l_state = 'code' THEN
            IF l_char = '-' AND l_next = '-' THEN
                l_state := 'line_comment';
                l_pos := l_pos + 2;
            ELSIF l_char = '/' AND l_next = '*' THEN
                l_state := 'block_comment';
                l_block_depth := 1;
                l_pos := l_pos + 2;
            -- Only treat e/E as the E-string prefix when it does not continue an
            -- identifier (for example a name ending in e).
            ELSIF (l_char = 'e' OR l_char = 'E')
                AND l_next = ''''
                AND (l_pos = 1 OR pg_catalog.strpos(l_tag_chars, l_prev) = 0) THEN
                l_state := 'estring';
                l_pos := l_pos + 2;
            ELSIF l_char = '''' THEN
                l_state := 'string';
                l_pos := l_pos + 1;
            ELSIF l_char = '"' THEN
                l_state := 'identifier';
                l_pos := l_pos + 1;
            ELSIF l_char = '$' AND l_next = '$' THEN
                l_dollar_tag := '$$';
                l_state := 'dollar';
                l_pos := l_pos + 2;
            ELSIF l_char = '$' AND pg_catalog.strpos(l_tag_first_chars, l_next) > 0 THEN
                l_scan := l_pos + 1;
                l_dollar_tag := '$';
                WHILE l_scan <= l_length
                    AND pg_catalog.strpos(l_tag_chars, pg_catalog.substr(i_value, l_scan, 1)) > 0 LOOP
                    l_dollar_tag := l_dollar_tag || pg_catalog.substr(i_value, l_scan, 1);
                    l_scan := l_scan + 1;
                END LOOP;
                IF pg_catalog.substr(i_value, l_scan, 1) = '$' THEN
                    l_dollar_tag := l_dollar_tag || '$';
                    l_state := 'dollar';
                    l_pos := l_scan + 1;
                ELSE
                    l_pos := l_pos + 1;
                END IF;
            ELSIF l_char IN ('(', '[', '{') THEN
                l_depth := l_depth + 1;
                l_pos := l_pos + 1;
            ELSIF l_char IN (')', ']', '}') THEN
                l_depth := greatest(l_depth - 1, 0);
                l_pos := l_pos + 1;
            ELSIF l_char = ',' AND l_depth = 0 THEN
                RETURN true;
            ELSE
                l_pos := l_pos + 1;
            END IF;
        ELSIF l_state = 'string' THEN
            -- Standard string: '' is an escaped quote, backslash is literal.
            IF l_char = '''' AND l_next = '''' THEN
                l_pos := l_pos + 2;
            ELSIF l_char = '''' THEN
                l_state := 'code';
                l_pos := l_pos + 1;
            ELSE
                l_pos := l_pos + 1;
            END IF;
        ELSIF l_state = 'estring' THEN
            -- E-string: backslash escapes the next character; '' also works.
            IF l_char = '\' THEN
                l_pos := l_pos + 2;
            ELSIF l_char = '''' AND l_next = '''' THEN
                l_pos := l_pos + 2;
            ELSIF l_char = '''' THEN
                l_state := 'code';
                l_pos := l_pos + 1;
            ELSE
                l_pos := l_pos + 1;
            END IF;
        ELSIF l_state = 'identifier' THEN
            IF l_char = '"' AND l_next = '"' THEN
                l_pos := l_pos + 2;
            ELSIF l_char = '"' THEN
                l_state := 'code';
                l_pos := l_pos + 1;
            ELSE
                l_pos := l_pos + 1;
            END IF;
        ELSIF l_state = 'line_comment' THEN
            -- A line comment ends at LF or CR (bare CR is a line ending on some
            -- platforms and would otherwise swallow the rest of the value).
            IF l_char = chr(10) OR l_char = chr(13) THEN
                l_state := 'code';
            END IF;
            l_pos := l_pos + 1;
        ELSIF l_state = 'block_comment' THEN
            -- PostgreSQL block comments nest, so track the depth.
            IF l_char = '/' AND l_next = '*' THEN
                l_block_depth := l_block_depth + 1;
                l_pos := l_pos + 2;
            ELSIF l_char = '*' AND l_next = '/' THEN
                l_block_depth := l_block_depth - 1;
                l_pos := l_pos + 2;
                IF l_block_depth = 0 THEN
                    l_state := 'code';
                END IF;
            ELSE
                l_pos := l_pos + 1;
            END IF;
        ELSIF l_state = 'dollar' THEN
            IF l_char = '$'
                AND pg_catalog.substr(i_value, l_pos, pg_catalog.length(l_dollar_tag)) = l_dollar_tag THEN
                l_state := 'code';
                l_pos := l_pos + pg_catalog.length(l_dollar_tag);
            ELSE
                l_pos := l_pos + 1;
            END IF;
        END IF;
    END LOOP;

    RETURN false;
END;
$func$;

COMMENT ON FUNCTION ddl_utils_lib.has_top_level_comma IS
    'Returns whether a value contains a comma outside parentheses, brackets, '
        'braces, string/dollar-quoted literals, quoted identifiers or comments. '
        'A NULL value returns false.';

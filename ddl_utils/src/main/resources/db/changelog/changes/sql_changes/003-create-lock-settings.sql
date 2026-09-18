CREATE TABLE ddl_utils.database_lock_settings
(
    id                 integer PRIMARY KEY NOT NULL,
    ddl_lock_timeout   integer NOT NULL,
    sleep_time         integer NOT NULL,
    statement_duration integer NOT NULL,
    CONSTRAINT database_lock_settings_id_check CHECK (id = 1),
    CONSTRAINT database_lock_settings_ddl_lock_timeout_check CHECK (ddl_lock_timeout >= 0),
    CONSTRAINT database_lock_settings_sleep_time_check CHECK (sleep_time >= 0),
    CONSTRAINT database_lock_settings_statement_duration_check CHECK (statement_duration >= 0)
);

INSERT INTO ddl_utils.database_lock_settings (id, ddl_lock_timeout, sleep_time, statement_duration)
VALUES (1, 100, 1000, 30000);

CREATE TABLE ddl_utils.schema_lock_settings
(
    schema_name        text PRIMARY KEY NOT NULL,
    ddl_lock_timeout   integer NOT NULL,
    sleep_time         integer NOT NULL,
    statement_duration integer NOT NULL,
    CONSTRAINT schema_lock_settings_ddl_lock_timeout_check CHECK (ddl_lock_timeout >= 0),
    CONSTRAINT schema_lock_settings_sleep_time_check CHECK (sleep_time >= 0),
    CONSTRAINT schema_lock_settings_statement_duration_check CHECK (statement_duration >= 0)
);

CREATE TABLE ddl_utils.table_lock_settings
(
    schema_name        text NOT NULL,
    table_name         text NOT NULL,
    ddl_lock_timeout   integer NOT NULL,
    sleep_time         integer NOT NULL,
    statement_duration integer NOT NULL,
    PRIMARY KEY (schema_name, table_name),
    CONSTRAINT table_lock_settings_ddl_lock_timeout_check CHECK (ddl_lock_timeout >= 0),
    CONSTRAINT table_lock_settings_sleep_time_check CHECK (sleep_time >= 0),
    CONSTRAINT table_lock_settings_statement_duration_check CHECK (statement_duration >= 0)
);

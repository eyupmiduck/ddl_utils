CREATE TABLE ddl_utils.lock_settings
(
    id                 integer PRIMARY KEY NOT NULL,
    ddl_lock_timeout   integer NOT NULL,
    sleep_time         integer NOT NULL,
    statement_duration integer NOT NULL,
    CONSTRAINT lock_settings_id_check CHECK (id = 1),
    CONSTRAINT lock_settings_ddl_lock_timeout_check CHECK (ddl_lock_timeout >= 0),
    CONSTRAINT lock_settings_sleep_time_check CHECK (sleep_time >= 0),
    CONSTRAINT lock_settings_statement_duration_check CHECK (statement_duration >= 0)
);

INSERT INTO ddl_utils.lock_settings (id, ddl_lock_timeout, sleep_time, statement_duration)
VALUES (1, 100, 1000, 30);

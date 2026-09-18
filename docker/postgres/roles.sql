-- Database initialization: create the application roles and grants.
-- Runs as the container superuser on first init, before Liquibase.
-- Roles are cluster-wide, so this runs once per container, not per database.

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ddl_utils_owner') THEN
            CREATE ROLE ddl_utils_owner LOGIN PASSWORD 'ddl_utils_owner';
        END IF;
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ddl_utils_caller') THEN
            CREATE ROLE ddl_utils_caller;
        END IF;
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ddl_utils_test') THEN
            CREATE ROLE ddl_utils_test LOGIN PASSWORD 'ddl_utils_test';
        END IF;
    END
$$;

-- The owner role creates the ddl_utils schema and the Liquibase tracking
-- tables, so it needs CREATE on the application database and on the public
-- schema of that database. The schema grant is per-database, so connect to
-- the application database first.
GRANT CREATE ON DATABASE ddl_utils TO ddl_utils_owner;

\connect ddl_utils
GRANT CREATE ON SCHEMA public TO ddl_utils_owner;

-- plpgsql_check is compiled into this image and used for static analysis of
-- the ddl_utils / ddl_utils_lib routines (for example
-- SELECT plpgsql_check_function('ddl_utils.get_lock_settings(..., ...)'::regprocedure)).
CREATE EXTENSION IF NOT EXISTS plpgsql_check;

-- The test role exercises the same privileges as a real application caller.
GRANT ddl_utils_caller TO ddl_utils_test;

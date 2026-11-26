-- Restore the default PUBLIC privileges that the forward 999 grant changeset
-- removed. This runs before 999's rollback in Liquibase's reverse order.
ALTER DEFAULT PRIVILEGES GRANT EXECUTE ON ROUTINES TO public;
ALTER DEFAULT PRIVILEGES GRANT USAGE ON TYPES TO public;

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.TestResources;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the default-privileges parity rollback restores the
 * database-wide PUBLIC EXECUTE/USAGE defaults that {@code 999-grant-privileges}
 * revoked. The released 999 rollback cannot restore those defaults, so this
 * changeset's rollback does it before 999's rollback in reverse order.
 */
class DefaultPrivilegesRollbackTest extends PostgresTestBase {

    private static final String BEFORE_FUNCTION = "default_acl_before";
    private static final String BEFORE_TYPE = "default_acl_before_type";
    private static final String AFTER_FUNCTION = "default_acl_after";
    private static final String AFTER_TYPE = "default_acl_after_type";

    /**
     * Objects created after the forward revoke have no PUBLIC privileges; after
     * executing the parity rollback, newly created routines and types inherit
     * the restored PUBLIC defaults.
     */
    @Test
    void rollbackRestoresPublicDefaults() throws Exception {
        applyForwardRevoke();
        createProbe(BEFORE_FUNCTION, BEFORE_TYPE);
        try {
            assertFalse(hasRoutineExecute(BEFORE_FUNCTION),
                    "the forward revoke must remove PUBLIC EXECUTE");
            assertFalse(hasTypeUsage(BEFORE_TYPE),
                    "the forward revoke must remove PUBLIC USAGE");

            applyRollback();

            createProbe(AFTER_FUNCTION, AFTER_TYPE);
            try {
                assertTrue(hasRoutineExecute(AFTER_FUNCTION),
                        "the rollback must restore PUBLIC EXECUTE for new routines");
                assertTrue(hasTypeUsage(AFTER_TYPE),
                        "the rollback must restore PUBLIC USAGE for new types");
            } finally {
                dropProbe(AFTER_FUNCTION, AFTER_TYPE);
            }
        } finally {
            dropProbe(BEFORE_FUNCTION, BEFORE_TYPE);
        }
    }

    private void applyForwardRevoke() throws Exception {
        executeAsOwner("ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON ROUTINES FROM public");
        executeAsOwner("ALTER DEFAULT PRIVILEGES REVOKE USAGE ON TYPES FROM public");
    }

    private void applyRollback() throws Exception {
        executeAsOwner(TestResources.read(
                "db/changelog/changes/rollback/999-default-privileges-parity-rollback.sql"));
    }

    private void createProbe(String function, String type) throws Exception {
        executeAsOwner("CREATE FUNCTION " + PUBLIC_SCHEMA + "." + function
                + "() RETURNS integer LANGUAGE sql AS 'SELECT 1'");
        executeAsOwner("CREATE TYPE " + PUBLIC_SCHEMA + "." + type + " AS ENUM ('a')");
    }

    private void dropProbe(String function, String type) throws Exception {
        executeAsOwner("DROP FUNCTION IF EXISTS " + PUBLIC_SCHEMA + "." + function + "()");
        executeAsOwner("DROP TYPE IF EXISTS " + PUBLIC_SCHEMA + "." + type);
    }

    private void executeAsOwner(String sql) throws Exception {
        try (Connection owner = openOwnerConnection(); Statement statement = owner.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean hasRoutineExecute(String function) {
        return dsl.fetchOne("SELECT pg_catalog.has_function_privilege(current_user, ?, 'EXECUTE') AS allowed",
                        PUBLIC_SCHEMA + "." + function + "()")
                .get("allowed", Boolean.class);
    }

    private boolean hasTypeUsage(String type) {
        return dsl.fetchOne("SELECT pg_catalog.has_type_privilege(current_user, ?, 'USAGE') AS allowed",
                        PUBLIC_SCHEMA + "." + type)
                .get("allowed", Boolean.class);
    }
}

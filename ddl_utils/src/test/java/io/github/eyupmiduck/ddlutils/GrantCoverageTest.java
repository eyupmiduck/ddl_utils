package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.GrantAssertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the grant changeset covers every routine. A routine added without a
 * matching entry in {@code 999-grant-privileges.sql} would otherwise remain
 * executable by PUBLIC (PostgreSQL's default) and therefore by the application
 * role as well. The ACL invariants delegate to the shared
 * {@link GrantAssertions}; this class adds the non-vacuity check and the
 * trigger-function exclusion the shared helper does not assert.
 */
class GrantCoverageTest extends PostgresTestBase {

    private static final List<String> ROUTINE_SCHEMAS = List.of("ddl_utils", "ddl_utils_lib");
    private static final String CALLER = "ddl_utils_caller";

    /**
     * Every routine in {@code ddl_utils} / {@code ddl_utils_lib} is executable
     * by {@code ddl_utils_caller} and not by PUBLIC. The trigger function
     * {@code ddl_utils.set_updated_at} is executable by neither, because it is
     * invoked by the trigger machinery rather than by callers.
     */
    @Test
    void everyRoutineIsGrantedToCallerAndRevokedFromPublic() throws Exception {
        for (String schema : ROUTINE_SCHEMAS) {
            Integer routines = dsl.fetchOne("""
                    SELECT count(*)::int
                    FROM pg_proc AS p
                    JOIN pg_namespace AS n ON n.oid = p.pronamespace
                    WHERE n.nspname = ? AND p.prokind IN ('f', 'p')
                    """, schema).get(0, Integer.class);
            assertTrue(routines > 0, () -> "no routines found in " + schema);
        }

        try (Connection connection = openTestConnection()) {
            GrantAssertions.assertNoRoutineExecutableByPublic(connection, ROUTINE_SCHEMAS);
            GrantAssertions.assertRoleCanExecuteEveryCallableRoutine(connection, ROUTINE_SCHEMAS, CALLER);
        }

        // The shared "every callable routine" assertion excludes trigger
        // functions, so enforce that the application role cannot call the shared
        // trigger function directly either.
        Integer triggerExecutable = dsl.fetchOne("""
                SELECT count(*)::int
                FROM pg_proc AS p
                JOIN pg_namespace AS n ON n.oid = p.pronamespace
                WHERE n.nspname = 'ddl_utils'
                  AND p.proname = 'set_updated_at'
                  AND p.prorettype = 'trigger'::pg_catalog.regtype
                  AND pg_catalog.has_function_privilege('ddl_utils_caller', p.oid, 'EXECUTE')
                """).get(0, Integer.class);
        assertEquals(0, triggerExecutable,
                "the trigger function must not be executable by ddl_utils_caller");
    }

    /**
     * The grant changeset revokes PUBLIC EXECUTE by default for routines the
     * migration role creates in {@code ddl_utils} / {@code ddl_utils_lib} from
     * then on, so a routine added by a later changeset cannot silently stay
     * executable by PUBLIC (and therefore by every role). A probe routine
     * created as the owner must have a stored ACL with no PUBLIC EXECUTE entry,
     * and no implicit caller grant either.
     */
    @Test
    void routinesCreatedLaterHaveNoPublicExecute() throws Exception {
        String probe = "default_acl_probe";
        try (Connection owner = openOwnerConnection();
             Statement statement = owner.createStatement()) {
            statement.execute("CREATE FUNCTION ddl_utils." + probe
                    + "() RETURNS integer LANGUAGE sql AS 'SELECT 1'");
        }
        try {
            Boolean aclStored = dsl.fetchOne("""
                    SELECT p.proacl IS NOT NULL
                    FROM pg_proc AS p
                    JOIN pg_namespace AS n ON n.oid = p.pronamespace
                    WHERE n.nspname = 'ddl_utils' AND p.proname = ?
                    """, probe).get(0, Boolean.class);
            assertTrue(aclStored,
                    "the revoked default must be recorded in the new routine's ACL");

            Integer publicExecute = dsl.fetchOne("""
                    SELECT count(*)::int
                    FROM pg_proc AS p
                    JOIN pg_namespace AS n ON n.oid = p.pronamespace
                    CROSS JOIN LATERAL pg_catalog.aclexplode(p.proacl) AS a
                    WHERE n.nspname = 'ddl_utils'
                      AND p.proname = ?
                      AND a.grantee = 0
                      AND a.privilege_type = 'EXECUTE'
                    """, probe).get(0, Integer.class);
            assertEquals(0, publicExecute,
                    "a routine created after the grant changeset must not keep PUBLIC EXECUTE");

            Boolean callerExecute = dsl.fetchOne("""
                    SELECT pg_catalog.has_function_privilege('ddl_utils_caller', p.oid, 'EXECUTE')
                    FROM pg_proc AS p
                    JOIN pg_namespace AS n ON n.oid = p.pronamespace
                    WHERE n.nspname = 'ddl_utils' AND p.proname = ?
                    """, probe).get(0, Boolean.class);
            assertFalse(callerExecute,
                    "a new routine has no caller grant until the changeset adds one");
        } finally {
            try (Connection owner = openOwnerConnection();
                 Statement statement = owner.createStatement()) {
                statement.execute("DROP FUNCTION IF EXISTS ddl_utils." + probe + "()");
            }
        }
    }
}

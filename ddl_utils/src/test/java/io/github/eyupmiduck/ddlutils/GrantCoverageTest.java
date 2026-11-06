package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.GrantAssertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the grant changeset covers every routine. A routine added without a
 * matching entry in {@code 999-grant-privileges.sql} would otherwise be
 * executable by PUBLIC (PostgreSQL's default) and not by the application role.
 */
class GrantCoverageTest extends PostgresTestBase {

    /**
     * Every routine in {@code ddl_utils} / {@code ddl_utils_lib} is executable
     * by {@code ddl_utils_caller} and not by PUBLIC. The trigger function
     * {@code ddl_utils.set_updated_at} is executable by neither, because it is
     * invoked by the trigger machinery rather than by callers.
     */
    @Test
    void everyRoutineIsGrantedToCallerAndRevokedFromPublic() {
        List<Record> routines = dsl.fetch("""
                SELECT n.nspname,
                       p.proname,
                       pg_catalog.has_function_privilege('ddl_utils_caller', p.oid, 'EXECUTE')
                           AS caller_execute,
                       COALESCE(
                           (SELECT bool_or(a.grantee = 0::oid)
                            FROM pg_catalog.aclexplode(p.proacl) AS a
                            WHERE a.privilege_type = 'EXECUTE'),
                           p.proacl IS NULL
                       ) AS public_execute
                FROM pg_catalog.pg_proc AS p
                    JOIN pg_catalog.pg_namespace AS n ON n.oid = p.pronamespace
                WHERE n.nspname IN ('ddl_utils', 'ddl_utils_lib')
                """);

        assertFalse(routines.isEmpty(), "expected routines in ddl_utils / ddl_utils_lib");

        for (Record routine : routines) {
            String name = routine.get("nspname", String.class) + "." + routine.get("proname", String.class);
            boolean isTriggerFunction = name.equals("ddl_utils.set_updated_at");

            assertEquals(!isTriggerFunction, routine.get("caller_execute", Boolean.class),
                    () -> name + ": ddl_utils_caller EXECUTE should be " + !isTriggerFunction);
            assertFalse(routine.get("public_execute", Boolean.class),
                    () -> name + ": PUBLIC must not have EXECUTE");
        }
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.GrantAssertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

/**
 * Verifies the grant changeset covers every routine. A routine added without a
 * matching entry in {@code 999-grant-privileges.sql} would otherwise be
 * executable by PUBLIC (PostgreSQL's default) and not by the application role.
 * The ACL invariants delegate to the shared {@link GrantAssertions}.
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
        try (Connection connection = openTestConnection()) {
            GrantAssertions.assertNoRoutineExecutableByPublic(connection, ROUTINE_SCHEMAS);
            GrantAssertions.assertRoleCanExecuteEveryCallableRoutine(connection, ROUTINE_SCHEMAS, CALLER);
        }
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.RoutineAssertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

/**
 * Guards the routine rollback files: every routine must have a
 * {@code DROP FUNCTION/PROCEDURE IF EXISTS <signature>} rollback that resolves
 * to an existing routine, and no signature may be dropped twice. Delegates to
 * the shared {@link RoutineAssertions#assertRollbackDropsMatchRoutines}.
 *
 * <p>Because {@code IF EXISTS} makes a stale DROP a silent no-op, a signature
 * change that is not propagated to the rollback would otherwise go unnoticed
 * until a rollback left the routine behind.
 */
class RollbackSignatureTest extends PostgresTestBase {

    /**
     * The rollback signatures of the function and procedure trees equal the
     * routines in {@code ddl_utils} and {@code ddl_utils_lib}.
     *
     * @throws Exception if the rollback files or the catalog cannot be read
     */
    @Test
    void rollbackDropsMatchTheRoutines() throws Exception {
        try (Connection connection = openTestConnection()) {
            RoutineAssertions.assertRollbackDropsMatchRoutines(connection,
                    List.of("ddl_utils", "ddl_utils_lib"),
                    List.of("db/changelog/changes/functions-rollback",
                            "db/changelog/changes/procedures-rollback"));
        }
    }
}

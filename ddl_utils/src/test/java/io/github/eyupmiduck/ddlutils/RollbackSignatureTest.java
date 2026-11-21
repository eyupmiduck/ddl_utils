package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.RollbackSignatureContract;
import io.github.eyupmiduck.changelogvalidator.testing.TestResources;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the routine rollback files, inheriting the shared
 * {@link RollbackSignatureContract}: every routine must have a
 * {@code DROP FUNCTION/PROCEDURE IF EXISTS <signature>} rollback that resolves
 * to an existing routine, and no signature may be dropped twice.
 *
 * <p>Because {@code IF EXISTS} makes a stale DROP a silent no-op, a signature
 * change that is not propagated to the rollback would otherwise go unnoticed
 * until a rollback left the routine behind.
 */
class RollbackSignatureTest extends RollbackSignatureContract {

    /**
     * The ddl_utils container and changelog configuration.
     *
     * @return the configuration
     */
    @Override
    protected Configuration configuration() {
        return PostgresTestBase.CONFIGURATION;
    }

    /**
     * The schemas that hold the project's routines, taken from the shared
     * constants so a schema rename cannot silently enumerate nothing.
     *
     * @return the routine schemas
     */
    @Override
    protected Collection<String> routineSchemas() {
        return PostgresTestBase.ROUTINE_SCHEMAS;
    }

    /**
     * The rollback trees for the functions and procedures.
     *
     * @return the rollback directories
     */
    @Override
    protected Collection<String> rollbackDirectories() {
        return PostgresTestBase.ROUTINE_ROLLBACK_DIRECTORIES;
    }

    /**
     * Every configured routine schema holds at least one routine and every
     * rollback directory holds at least one SQL file, so the inherited contract
     * cannot pass by inspecting an empty set on either side.
     *
     * @throws java.io.IOException if a rollback directory cannot be read
     */
    @Test
    void routineSchemasAndRollbackDirectoriesArePopulated() throws java.io.IOException {
        PostgresTestBase.assertRoutineSchemasArePopulated(dsl, routineSchemas());
        assertFalse(rollbackDirectories().isEmpty(), "at least one rollback directory is required");
        for (String directory : rollbackDirectories()) {
            assertFalse(TestResources.readSqlFiles(directory).isEmpty(),
                    () -> "no rollback SQL files found in " + directory);
        }
    }
}

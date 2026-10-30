package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.RollbackSignatureContract;

import java.util.Collection;
import java.util.List;

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
     * The schemas that hold the project's routines.
     *
     * @return the routine schemas
     */
    @Override
    protected Collection<String> routineSchemas() {
        return List.of("ddl_utils", "ddl_utils_lib");
    }

    /**
     * The rollback trees for the functions and procedures.
     *
     * @return the rollback directories
     */
    @Override
    protected Collection<String> rollbackDirectories() {
        return List.of("db/changelog/changes/functions-rollback",
                "db/changelog/changes/procedures-rollback");
    }
}

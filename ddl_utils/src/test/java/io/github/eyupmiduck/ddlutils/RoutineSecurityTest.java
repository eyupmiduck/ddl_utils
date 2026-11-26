package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import io.github.eyupmiduck.changelogvalidator.testing.RoutineSecurityContract;

import java.util.Set;

/**
 * Verifies the routine security conventions, inheriting the shared
 * {@link RoutineSecurityContract}: only deliberate {@code SECURITY DEFINER}
 * routines are allowed, definers and catalog-inspecting routines pin a safe
 * {@code search_path}, and no routine uses a caller-controlled path.
 */
class RoutineSecurityTest extends RoutineSecurityContract {

    /**
     * The lock-settings writers are {@code SECURITY DEFINER} so a caller with
     * only {@code SELECT} on the settings tables can maintain them; the explicit
     * orphan-prune function is a definer for the same reason.
     *
     * @return the allowed definers
     */
    @Override
    protected Set<String> allowedSecurityDefiners() {
        return Set.of(
                "ddl_utils.set_database_lock_settings",
                "ddl_utils.set_schema_lock_settings",
                "ddl_utils.set_table_lock_settings",
                "ddl_utils.clear_schema_lock_settings",
                "ddl_utils.clear_table_lock_settings",
                "ddl_utils.move_table_lock_settings",
                "ddl_utils.prune_orphaned_lock_settings");
    }

    /**
     * The multi-step {@code ensure_*} routines are procedures, which is what lets
     * them commit between {@code ALTER TABLE} steps.
     *
     * @return the expected procedures
     */
    @Override
    protected Set<String> expectedProcedures() {
        return Set.of(
                "ddl_utils.ensure_not_null",
                "ddl_utils.ensure_check_constraint",
                "ddl_utils.ensure_foreign_key");
    }

    /**
     * The ddl_utils profile, so this contract uses the project's roles, image,
     * changelog and schemas rather than the library defaults.
     *
     * @return the ddl_utils profile
     */
    @Override
    protected ProjectProfile projectProfile() {
        return PostgresTestBase.PROFILE;
    }
}

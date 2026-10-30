package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.PublishedRelease;
import io.github.eyupmiduck.changelogvalidator.testing.UpgradeContract;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the incremental upgrade from the most recent published release of
 * ddl_utils to the current codebase, inheriting the shared
 * {@link UpgradeContract}: the released changelog's SQL is applied to a scratch
 * database, then the current changelog, so an upgrade that only works on an
 * empty database fails here.
 *
 * <p>The previous release is resolved from GitHub Packages automatically, and
 * the container configuration is shared with the regular test base (see
 * {@link PostgresTestBase}).
 */
class LiquibaseUpgradeTest extends UpgradeContract {

    @Override
    protected PublishedRelease.Coordinates releaseCoordinates() {
        return new PublishedRelease.Coordinates(
                "https://maven.pkg.github.com/eyupmiduck/ddl_utils",
                "io.github.eyupmiduck", "ddl_utils",
                System.getProperty("upgrade.current.version", "0.0.0-SNAPSHOT"));
    }

    /**
     * The schemas the changelog manages, compared between the upgraded database
     * and a from-scratch build.
     *
     * @return the managed schemas
     */
    @Override
    protected Collection<String> managedSchemas() {
        return List.of("ddl_utils", "ddl_utils_lib");
    }

    /**
     * Returns the same configuration as the regular test base, so the two
     * classes cannot drift.
     *
     * @return the configuration
     */
    @Override
    protected Configuration configuration() {
        return PostgresTestBase.CONFIGURATION;
    }

    /**
     * The upgraded database has both ddl_utils schemas.
     */
    @Override
    protected void assertUpgradedDatabase() {
        assertEquals(2, upgradeDsl().fetchOne(
                                "SELECT count(*) FROM pg_namespace WHERE nspname IN ('ddl_utils', 'ddl_utils_lib')")
                        .get(0, Integer.class),
                "the upgraded database should have the ddl_utils schemas");
    }
}

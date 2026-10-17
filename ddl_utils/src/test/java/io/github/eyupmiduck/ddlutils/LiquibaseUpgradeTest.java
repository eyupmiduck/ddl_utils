package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.PublishedRelease;
import io.github.eyupmiduck.changelogvalidator.testing.UpgradeTestBase;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Exercises the incremental upgrade from the most recent published release of
 * ddl_utils to the current codebase: the released changelog's SQL is applied to
 * a scratch database, then the current changelog, so an upgrade that only works
 * on an empty database fails here.
 *
 * <p>It reuses the shared {@link UpgradeTestBase} and the ddl_utils container
 * configuration (see {@link PostgresTestBase}); the previous release is resolved
 * from GitHub Packages automatically.
 */
class LiquibaseUpgradeTest extends UpgradeTestBase {

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
     */
    @Override
    protected Collection<String> managedSchemas() {
        return List.of("ddl_utils", "ddl_utils_lib");
    }

    /**
     * Installs the same extension as the template, so the container's shared
     * template is identical whichever test class prepares it first.
     */
    @Override
    protected void installExtensions(String database) throws SQLException {
        try (Connection admin = openAdminConnection(database)) {
            PostgresTestBase.installProjectExtensions(admin);
        }
    }

    @Override
    protected String defaultPostgresImage() {
        return PostgresTestBase.POSTGRES_IMAGE;
    }

    @Override
    protected String databaseName() {
        return PostgresTestBase.DATABASE;
    }

    @Override
    protected String ownerUser() {
        return PostgresTestBase.OWNER_USER;
    }

    @Override
    protected String ownerPassword() {
        return PostgresTestBase.OWNER_PASSWORD;
    }

    @Override
    protected String testUser() {
        return PostgresTestBase.TEST_USER;
    }

    @Override
    protected String testPassword() {
        return PostgresTestBase.TEST_PASSWORD;
    }

    @Override
    protected String databaseChangeLogTableName() {
        return PostgresTestBase.DATABASE_CHANGELOG_TABLE;
    }

    @Override
    protected String databaseChangeLogLockTableName() {
        return PostgresTestBase.DATABASE_CHANGELOG_LOCK_TABLE;
    }

    /**
     * The published release contributes changesets, the current changelog
     * applies cleanly on top, and the upgraded database has the ddl_utils
     * schemas.
     */
    @Test
    void upgradesThePublishedReleaseToTheCurrentChangelog() {
        assertFalse(report().previousRelease().isEmpty(),
                "the published release should apply at least one changeset");
        assertEquals(2, upgradeDsl().fetchOne(
                                "SELECT count(*) FROM pg_namespace WHERE nspname IN ('ddl_utils', 'ddl_utils_lib')")
                        .get(0, Integer.class),
                "the upgraded database should have the ddl_utils schemas");
    }
}

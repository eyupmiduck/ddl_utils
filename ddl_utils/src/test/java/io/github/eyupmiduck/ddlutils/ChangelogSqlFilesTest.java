package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogSqlFilesContract;

/**
 * Verifies that every SQL file in the Liquibase changelog is referenced by a
 * changelog XML file reachable from the master changelog, inheriting the shared
 * {@link ChangelogSqlFilesContract}.
 */
class ChangelogSqlFilesTest extends ChangelogSqlFilesContract {
}

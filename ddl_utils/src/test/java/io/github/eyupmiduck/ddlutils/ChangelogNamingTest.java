package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.ChangelogValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the naming conventions enforced on the Liquibase changelog: every
 * SQL file and every changeSet id must start with a three-digit, zero-padded
 * prefix (for example {@code 001-create-schema}), followed by a hyphen or an
 * underscore.
 */
class ChangelogNamingTest {

    /**
     * Scans the changelog changes directory and asserts that no SQL file name
     * violates the {@code NNN-name.sql} pattern.
     */
    @Test
    void sqlFilesHaveThreeDigitPrefix() throws IOException, URISyntaxException {
        URL changesUrl = getClass().getClassLoader().getResource("db/changelog/changes");
        assertNotNull(changesUrl, "changelog directory must be on the test classpath");
        Path changesRoot = Path.of(changesUrl.toURI());

        List<Path> invalid = ChangelogValidator.findInvalidlyNamedSqlFiles(changesRoot);

        assertTrue(invalid.isEmpty(), "Invalidly named SQL files: " + invalid);
    }

    /**
     * Traverses the changelog graph from the master file and asserts that no
     * changeSet id violates the {@code NNN-name} pattern.
     */
    @Test
    void changeSetsHaveThreeDigitPrefix() throws IOException, URISyntaxException {
        URL changelogUrl = getClass().getClassLoader().getResource("db/changelog");
        assertNotNull(changelogUrl, "changelog directory must be on the test classpath");
        Path changelogRoot = Path.of(changelogUrl.toURI());
        Path master = changelogRoot.resolve("db.changelog-master.xml");

        List<ChangelogValidator.InvalidChangeSet> invalid =
                ChangelogValidator.findInvalidlyNamedChangeSets(changelogRoot, master);

        assertTrue(invalid.isEmpty(), "Invalidly named changeSets: " + invalid);
    }
}

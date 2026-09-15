package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.ChangelogValidator;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every XML and SQL file in the Liquibase changelog changes
 * directory is named with a three-digit, zero-padded prefix (for example
 * {@code 001-create-schema.xml}); the prefix may be followed by a hyphen or
 * an underscore.
 */
class ChangelogFileNamingTest {

    /**
     * Scans the changelog changes directory and asserts that no file name
     * violates the {@code NNN-name.ext} pattern.
     */
    @Test
    void allChangelogFilesHaveThreeDigitPrefix() throws Exception {
        URL changesUrl = getClass().getClassLoader().getResource("db/changelog/changes");
        Path changesRoot = Path.of(changesUrl.toURI());

        List<Path> invalid = ChangelogValidator.findInvalidlyNamedFiles(changesRoot);

        assertTrue(invalid.isEmpty(), "Invalidly named changelog files: " + invalid);
    }
}

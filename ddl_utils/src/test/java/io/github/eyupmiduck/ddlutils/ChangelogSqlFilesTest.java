package io.github.eyupmiduck.ddlutils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.eyupmiduck.changelogvalidator.ChangelogValidator;

/**
 * Verifies that every SQL file in the Liquibase changelog is referenced by a
 * changelog XML file, so no SQL file is orphaned.
 */
class ChangelogSqlFilesTest {

    /**
     * Scans the changelog directory and asserts that no {@code .sql} file is
     * orphaned (unreferenced by any changelog XML).
     */
    @Test
    void noOrphanedSqlFiles() throws Exception {
        URL changelogUrl = getClass().getClassLoader().getResource("db/changelog");
        Path changelogRoot = Path.of(changelogUrl.toURI());

        List<Path> orphaned = ChangelogValidator.findOrphanedSqlFiles(changelogRoot);

        assertTrue(orphaned.isEmpty(), "Orphaned SQL files: " + orphaned);
    }
}

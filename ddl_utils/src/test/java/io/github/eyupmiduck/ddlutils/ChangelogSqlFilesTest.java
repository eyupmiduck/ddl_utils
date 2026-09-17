package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.ChangelogValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every SQL file in the Liquibase changelog is referenced by a
 * changelog XML file reachable from the master changelog, so no SQL file is
 * orphaned.
 */
class ChangelogSqlFilesTest {

    /**
     * Traverses the changelog graph from the master file and asserts that no
     * {@code .sql} file is orphaned (unreferenced by any reachable changelog
     * XML).
     */
    @Test
    void noOrphanedSqlFiles() throws Exception {
        URL changelogUrl = getClass().getClassLoader().getResource("db/changelog");
        URL masterUrl = getClass().getClassLoader().getResource("db/changelog/db.changelog-master.xml");
        Assertions.assertNotNull(changelogUrl);
        Assertions.assertNotNull(masterUrl);
        Path changelogRoot = Path.of(changelogUrl.toURI());
        Path master = Path.of(masterUrl.toURI());

        // Sanity check so the assertion below cannot pass while the graph
        // references nothing (a misresolved root or an empty changelog).
        List<Path> referenced = ChangelogValidator.findReferencedSqlFiles(changelogRoot, master);
        assertFalse(referenced.isEmpty(), "expected the changelog graph to reference SQL files");

        List<Path> orphaned = ChangelogValidator.findOrphanedSqlFiles(changelogRoot, master);

        assertTrue(orphaned.isEmpty(), "Orphaned SQL files: " + orphaned);
    }
}

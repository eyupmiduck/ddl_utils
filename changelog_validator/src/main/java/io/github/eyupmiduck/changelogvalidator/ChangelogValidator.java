package io.github.eyupmiduck.changelogvalidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Validates a Liquibase changelog directory for orphaned SQL files.
 */
public final class ChangelogValidator {

    private ChangelogValidator() {
    }

    /**
     * Finds {@code .sql} files under {@code changelogRoot} that are not
     * referenced by any changelog XML file via a {@code <sqlFile>} element.
     *
     * @param changelogRoot the changelog directory to scan
     * @return the orphaned SQL files, relative to {@code changelogRoot}
     * @throws IOException if the directory cannot be read
     */
    public static List<Path> findOrphanedSqlFiles(Path changelogRoot) throws IOException {
        Set<Path> sqlFiles = findSqlFiles(changelogRoot);
        Set<Path> referenced = findReferencedSqlFiles(changelogRoot);

        List<Path> orphaned = new ArrayList<>();
        for (Path sqlFile : sqlFiles) {
            if (!referenced.contains(sqlFile)) {
                orphaned.add(sqlFile);
            }
        }
        orphaned.sort(Path::compareTo);
        return orphaned;
    }

    private static Set<Path> findSqlFiles(Path root) throws IOException {
        Set<Path> result = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .forEach(p -> result.add(root.relativize(p).normalize()));
        }
        return result;
    }

    private static Set<Path> findReferencedSqlFiles(Path root) throws IOException {
        Set<Path> result = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> xmlFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .toList();
            for (Path xmlFile : xmlFiles) {
                result.addAll(referencesIn(xmlFile, root));
            }
        }
        return result;
    }

    private static Set<Path> referencesIn(Path xmlFile, Path root) {
        Set<Path> result = new HashSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(xmlFile.toFile());
            NodeList sqlFiles = document.getElementsByTagName("sqlFile");
            for (int i = 0; i < sqlFiles.getLength(); i++) {
                Element element = (Element) sqlFiles.item(i);
                String path = element.getAttribute("path");
                if (path == null || path.isBlank()) {
                    continue;
                }
                boolean relativeToChangelogFile =
                        "true".equalsIgnoreCase(element.getAttribute("relativeToChangelogFile"));
                Path resolved = relativeToChangelogFile
                        ? xmlFile.getParent().resolve(path)
                        : root.resolve(path);
                result.add(root.relativize(resolved).normalize());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + xmlFile, e);
        }
        return result;
    }
}

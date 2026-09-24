package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the routine rollback files. Each routine must have a
 * {@code DROP FUNCTION/PROCEDURE IF EXISTS <signature>} rollback, and each such
 * statement must resolve to an existing routine. Because {@code IF EXISTS}
 * makes a stale DROP a silent no-op, a signature change that is not propagated
 * to the rollback would otherwise go unnoticed until a rollback left the routine
 * behind.
 */
class RollbackSignatureTest extends PostgresTestBase {

    /**
     * Matches a rollback {@code DROP} and captures its signature (the part
     * between {@code IF EXISTS} and the semicolon).
     */
    private static final Pattern DROP = Pattern.compile(
            "DROP\\s+(?:FUNCTION|PROCEDURE)\\s+IF\\s+EXISTS\\s+(.+?);",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /**
     * The set of routines the rollback files drop equals the set of routines in
     * the two schemas: no routine lacks a rollback, and no rollback is stale.
     */
    @Test
    void rollbackDropsMatchTheRoutines() throws IOException, URISyntaxException {
        Set<Long> routineOids = new HashSet<>(dsl.fetch("""
                        SELECT p.oid
                        FROM pg_catalog.pg_proc AS p
                            JOIN pg_catalog.pg_namespace AS n ON n.oid = p.pronamespace
                        WHERE n.nspname IN ('ddl_utils', 'ddl_utils_lib')
                        """)
                .getValues(0, Long.class));

        Set<Long> droppedOids = new HashSet<>();
        for (String signature : rollbackSignatures()) {
            Long oid = dsl.fetchOne("SELECT pg_catalog.to_regprocedure(?)::oid AS oid", signature)
                    .get("oid", Long.class);
            assertNotNull(oid, () -> "rollback DROP does not resolve: " + signature);
            droppedOids.add(oid);
        }

        assertEquals(routineOids, droppedOids,
                "every routine needs a matching rollback DROP, and no rollback DROP may be stale");
    }

    /**
     * Reads every rollback {@code DROP} signature from the {@code functions-rollback}
     * and {@code procedures-rollback} trees on the test classpath.
     */
    private Set<String> rollbackSignatures() throws IOException, URISyntaxException {
        Set<String> signatures = new HashSet<>();
        for (String dir : List.of(
                "db/changelog/changes/functions-rollback",
                "db/changelog/changes/procedures-rollback")) {
            URL url = getClass().getClassLoader().getResource(dir);
            assertNotNull(url, dir + " must be on the test classpath");
            try (Stream<Path> files = Files.walk(Path.of(url.toURI()))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                    Matcher matcher = DROP.matcher(Files.readString(file));
                    while (matcher.find()) {
                        signatures.add(matcher.group(1).replaceAll("\\s+", " ").trim());
                    }
                }
            }
        }
        assertFalse(signatures.isEmpty(), "expected rollback DROP statements");
        return signatures;
    }
}

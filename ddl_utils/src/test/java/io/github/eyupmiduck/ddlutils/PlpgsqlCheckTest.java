package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the {@code plpgsql_check} static analyser over every routine in the
 * {@code ddl_utils} and {@code ddl_utils_lib} schemas with all warnings enabled,
 * and fails on any finding that is not accepted in
 * {@code plpgsql-check-whitelist.yml}.
 *
 * <p>The extension is compiled into the custom image and installed into the
 * template database, so each cloned test database (and the dev database, via
 * {@code docker/postgres/roles.sql}) has it.
 */
class PlpgsqlCheckTest extends PostgresTestBase {

    private static final String WHITELIST = "plpgsql-check-whitelist.yml";

    /**
     * Every plpgsql_check finding matches a whitelist entry, and every
     * whitelist entry matches a finding (so the whitelist cannot go stale).
     */
    @Test
    void routinesPassPlpgsqlCheck() throws Exception {
        List<Finding> findings = allFindings();
        List<AllowedFinding> allowed = loadWhitelist();

        List<String> unexpected = new ArrayList<>();
        Set<Integer> matched = new HashSet<>();
        for (Finding finding : findings) {
            boolean accepted = false;
            for (int i = 0; i < allowed.size(); i++) {
                if (allowed.get(i).matches(finding)) {
                    accepted = true;
                    matched.add(i);
                    break;
                }
            }
            if (!accepted) {
                unexpected.add(finding.describe());
            }
        }

        List<String> stale = new ArrayList<>();
        for (int i = 0; i < allowed.size(); i++) {
            if (!matched.contains(i)) {
                stale.add(allowed.get(i).describe());
            }
        }

        assertEquals(List.of(), unexpected,
                () -> "unexpected plpgsql_check findings:\n" + String.join("\n", unexpected));
        assertEquals(List.of(), stale,
                () -> "stale whitelist entries:\n" + String.join("\n", stale));
    }

    private List<Finding> allFindings() throws Exception {
        List<Finding> findings = new ArrayList<>();
        try (Connection owner = openOwnerConnection();
             Statement statement = owner.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT n.nspname, p.proname, (issue).lineno, (issue).level,
                            (issue).statement, (issue).message
                     FROM pg_catalog.pg_proc AS p
                     JOIN pg_catalog.pg_namespace AS n ON n.oid = p.pronamespace
                     CROSS JOIN LATERAL plpgsql_check_function_tb(
                             p.oid::regprocedure, all_warnings => true
                         ) AS issue
                     WHERE n.nspname IN ('ddl_utils', 'ddl_utils_lib')
                         AND p.prokind = 'f'
                     ORDER BY 1, 2, 3
                     """)) {
            while (resultSet.next()) {
                findings.add(new Finding(
                        resultSet.getString(1),
                        resultSet.getString(2),
                        resultSet.getInt(3),
                        resultSet.getString(4),
                        resultSet.getString(5),
                        resultSet.getString(6)));
            }
        }
        return findings;
    }

    private List<AllowedFinding> loadWhitelist() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(WHITELIST)) {
            assertNotNull(in, WHITELIST + " not found on the test classpath");
            Object loaded = new Yaml().load(in);
            assertTrue(loaded instanceof List, WHITELIST + " must contain a YAML list");

            List<AllowedFinding> allowed = new ArrayList<>();
            for (Object item : (List<?>) loaded) {
                assertTrue(item instanceof Map, WHITELIST + " entries must be mappings");
                Map<?, ?> entry = (Map<?, ?>) item;
                allowed.add(new AllowedFinding(
                        asString(entry.get("schema")),
                        asString(entry.get("function")),
                        asString(entry.get("level")),
                        asString(entry.get("statement")),
                        asString(entry.get("message"))));
            }
            return allowed;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * One finding reported by {@code plpgsql_check_function_tb}.
     */
    private record Finding(String schema, String function, int line, String level,
                           String statement, String message) {
        String describe() {
            return schema + "." + function + ":" + line + ": " + level + ": " + message;
        }
    }

    /**
     * One accepted finding from the whitelist; a null field matches anything.
     */
    private record AllowedFinding(String schema, String function, String level,
                                  String statement, String message) {
        boolean matches(Finding finding) {
            return matches(schema, finding.schema())
                    && matches(function, finding.function())
                    && matches(level, finding.level())
                    && matches(statement, finding.statement())
                    && matches(message, finding.message());
        }

        String describe() {
            return schema + "." + function + " " + level + ": " + message;
        }

        private static boolean matches(String expected, String actual) {
            return expected == null || expected.equals(actual);
        }
    }
}

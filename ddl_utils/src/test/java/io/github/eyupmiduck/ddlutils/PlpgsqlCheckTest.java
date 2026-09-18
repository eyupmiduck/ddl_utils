package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the {@code plpgsql_check} static analyser over every routine in the
 * {@code ddl_utils} and {@code ddl_utils_lib} schemas. The extension is
 * compiled into the custom image and installed into the template database, so
 * each cloned test database (and the dev database, via
 * {@code docker/postgres/roles.sql}) has it.
 */
class PlpgsqlCheckTest extends PostgresTestBase {

    /**
     * plpgsql_check reports no warning or error for any routine, with
     * {@code all_warnings => true} so performance, security, compatibility and
     * extra warnings are all enabled. The check runs as the schema owner, which
     * owns the routines.
     */
    @Test
    void routinesPassPlpgsqlCheck() throws Exception {
        List<String> findings = new ArrayList<>();
        try (Connection owner = openOwnerConnection();
             Statement statement = owner.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT n.nspname || '.' || p.proname || ': ' || issue
                     FROM pg_catalog.pg_proc AS p
                     JOIN pg_catalog.pg_namespace AS n ON n.oid = p.pronamespace
                     CROSS JOIN LATERAL plpgsql_check_function(p.oid::regprocedure, all_warnings => true) AS issue
                     WHERE n.nspname IN ('ddl_utils', 'ddl_utils_lib')
                         AND p.prokind = 'f'
                     ORDER BY 1
                     """)) {
            while (resultSet.next()) {
                findings.add(resultSet.getString(1));
            }
        }

        assertEquals(List.of(), findings,
                () -> "plpgsql_check findings:\n" + String.join("\n", findings));
    }
}

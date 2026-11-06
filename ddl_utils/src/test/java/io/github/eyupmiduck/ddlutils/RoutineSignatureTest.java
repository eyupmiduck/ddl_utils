package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that every routine name in the {@code ddl_utils} and
 * {@code ddl_utils_lib} schemas has a single signature. A parameter-type change
 * in a {@code CREATE OR REPLACE} body creates a new overload and leaves the old
 * one installed; this test fails the build before such a stale signature can
 * reach a release.
 *
 * <p>A deliberate overload must be added to {@link #ALLOWED_OVERLOADS} with its
 * schema-qualified name, so the exception is visible in review.
 */
class RoutineSignatureTest extends PostgresTestBase {

    /**
     * Routines that are intentionally overloaded. Keep this empty unless a
     * deliberate overload is part of the API; the value is {@code schema.name}.
     */
    private static final Set<String> ALLOWED_OVERLOADS = Set.of();

    /**
     * No routine name has more than one signature outside the allow-list.
     */
    @Test
    void everyRoutineHasOneSignatureUnlessAllowed() {
        List<Record> duplicates = dsl.fetch("""
                SELECT n.nspname AS schema_name,
                       p.proname AS routine_name,
                       pg_catalog.string_agg(
                               pg_catalog.pg_get_function_identity_arguments(p.oid),
                               ' | ' ORDER BY p.oid) AS signatures
                FROM pg_catalog.pg_proc AS p
                JOIN pg_catalog.pg_namespace AS n ON n.oid = p.pronamespace
                WHERE n.nspname IN ('ddl_utils', 'ddl_utils_lib')
                  AND p.prokind IN ('f', 'p')
                GROUP BY n.nspname, p.proname
                HAVING pg_catalog.count(*) > 1
                ORDER BY 1, 2
                """);

        List<String> unexpected = new ArrayList<>();
        for (Record row : duplicates) {
            String name = row.get("schema_name", String.class) + "."
                    + row.get("routine_name", String.class);
            if (!ALLOWED_OVERLOADS.contains(name)) {
                unexpected.add(name + "(" + row.get("signatures", String.class) + ")");
            }
        }

        assertEquals(List.of(), unexpected,
                () -> "unexpected routine overloads (add a deliberate overload to ALLOWED_OVERLOADS): "
                        + unexpected);
    }
}

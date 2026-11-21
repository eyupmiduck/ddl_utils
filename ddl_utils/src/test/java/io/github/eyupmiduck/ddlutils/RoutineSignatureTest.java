package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that every routine name in the {@code ddl_utils} and
 * {@code ddl_utils_lib} schemas has a single signature. A parameter-type change
 * in a {@code CREATE OR REPLACE} body creates a new overload and leaves the old
 * one installed; this test fails the build before such a stale signature can
 * reach a release.
 *
 * <p>A deliberate overload must be added to {@link #ALLOWED_OVERLOADS} with its
 * schema-qualified name, so the exception is visible in review. The allow-list
 * itself cannot rot: an entry that no longer corresponds to an actual overload
 * fails the build too, so a future accidental overload of that name cannot hide
 * behind a stale exception.
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
        assertRoutineSchemasArePopulated(dsl, ROUTINE_SCHEMAS);

        List<String> unexpected = new ArrayList<>();
        for (Record row : overloadedRoutines()) {
            String name = routineName(row);
            if (!ALLOWED_OVERLOADS.contains(name)) {
                unexpected.add(name + "(" + row.get("signatures", String.class) + ")");
            }
        }

        assertEquals(List.of(), unexpected,
                () -> "unexpected routine overloads (add a deliberate overload to ALLOWED_OVERLOADS): "
                        + unexpected);
    }

    /**
     * Every allow-list entry still corresponds to a real overload, so a removed
     * or renamed overload cannot leave a stale exception that would let a new
     * overload of that name pass silently.
     */
    @Test
    void allowedOverloadsAreStillReal() {
        Set<String> overloaded = new TreeSet<>();
        for (Record row : overloadedRoutines()) {
            overloaded.add(routineName(row));
        }

        Set<String> stale = new TreeSet<>(ALLOWED_OVERLOADS);
        stale.removeAll(overloaded);

        assertEquals(Set.of(), stale,
                () -> "ALLOWED_OVERLOADS entries without an actual overload: " + stale);
    }

    /**
     * Returns one row per deliberately or accidentally overloaded routine name,
     * with the installed signatures aggregated.
     *
     * @return the overloaded routines
     */
    private List<Record> overloadedRoutines() {
        String placeholders = String.join(", ", Collections.nCopies(ROUTINE_SCHEMAS.size(), "?"));
        return dsl.fetch("""
                SELECT n.nspname AS schema_name,
                       p.proname AS routine_name,
                       pg_catalog.string_agg(
                               pg_catalog.pg_get_function_identity_arguments(p.oid),
                               ' | ' ORDER BY p.oid) AS signatures
                FROM pg_catalog.pg_proc AS p
                JOIN pg_catalog.pg_namespace AS n ON n.oid = p.pronamespace
                WHERE n.nspname IN (%s)
                  AND p.prokind IN ('f', 'p')
                GROUP BY n.nspname, p.proname
                HAVING pg_catalog.count(*) > 1
                ORDER BY 1, 2
                """.formatted(placeholders), ROUTINE_SCHEMAS.toArray());
    }

    /**
     * Returns the {@code schema.name} key of a routine row.
     *
     * @param row a row from {@link #overloadedRoutines()}
     * @return the schema-qualified routine name
     */
    private static String routineName(Record row) {
        return row.get("schema_name", String.class) + "." + row.get("routine_name", String.class);
    }
}

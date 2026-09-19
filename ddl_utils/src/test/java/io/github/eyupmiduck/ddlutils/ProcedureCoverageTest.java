package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.PlpgsqlCheck;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that the routine tooling covers stored procedures, not just functions:
 * the {@code ensure_*} procedures exist as procedures, and {@code plpgsql_check}
 * reports findings for a deliberately broken procedure (so a future broken
 * procedure cannot slip through the analyser).
 */
class ProcedureCoverageTest extends PostgresTestBase {

    /**
     * The multi-step routines are procedures ({@code prokind = 'p'}), which is
     * what lets them COMMIT between ALTER TABLE steps.
     */
    @Test
    void multiStepRoutinesAreProcedures() {
        List<String> procedures = dsl.fetch("""
                        SELECT proname
                        FROM pg_proc
                        WHERE pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'ddl_utils')
                            AND prokind = 'p'
                        """)
                .getValues(0, String.class);

        assertTrue(procedures.containsAll(
                        List.of("ensure_not_null", "ensure_check_constraint", "ensure_foreign_key")),
                () -> "procedures not found; found: " + procedures);
    }

    /**
     * {@code plpgsql_check} reports findings for a broken procedure, proving the
     * analyser inspects procedures and not only functions.
     */
    @Test
    void plpgsqlCheckReportsBrokenProcedure() throws Exception {
        dsl.execute("""
                CREATE OR REPLACE PROCEDURE public.broken_procedure_probe()
                LANGUAGE plpgsql AS $$
                DECLARE l_x int;
                BEGIN
                    SELECT nonexistent_col INTO l_x FROM pg_class;
                END;
                $$
                """);
        try (Connection owner = openOwnerConnection()) {
            List<PlpgsqlCheck.Finding> findings =
                    PlpgsqlCheck.findFindings(owner, List.of("public"));

            assertFalse(findings.isEmpty(),
                    "plpgsql_check found nothing wrong with a deliberately broken procedure");
        } finally {
            dsl.execute("DROP PROCEDURE IF EXISTS public.broken_procedure_probe()");
        }
    }
}

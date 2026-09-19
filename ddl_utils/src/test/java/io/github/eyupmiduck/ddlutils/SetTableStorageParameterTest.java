package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@code ddl_utils_lib.set_table_storage_parameter}: it builds a
 * {@code SET (name = value)} fragment and applies it through
 * {@code ddl_utils_lib.alter_table}, restricting the parameter to the set
 * PostgreSQL accepts under SHARE UPDATE EXCLUSIVE.
 */
class SetTableStorageParameterTest extends PostgresTestBase {

    private static final String TARGET = "set_table_storage_parameter_target";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Sets fillfactor, which is stored in the table's reloptions.
     */
    @Test
    void setsFillfactor() {
        setParameter("fillfactor", "70");

        assertEquals("70", option("fillfactor"));
    }

    /**
     * Sets a parallel_workers parameter, which is stored in reloptions.
     */
    @Test
    void setsParallelWorkers() {
        setParameter("parallel_workers", "4");

        assertEquals("4", option("parallel_workers"));
    }

    /**
     * An unsupported parameter is rejected, so a rewrite-inducing option cannot
     * be reached through this helper.
     */
    @Test
    void rejectsUnsupportedParameter() {
        assertSqlState("22023", () -> setParameter("autovacuum", "true"));
        assertSqlState("22023", () -> setParameter("oids", "true"));
    }

    /**
     * Rejects null arguments through their domains.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> setParameter(null, "70"));
        assertDomainViolation(() -> setParameter("fillfactor", null));
    }

    private String option(String name) {
        String options = dsl.fetchOne(
                """
                        SELECT array_to_string(c.reloptions, ',')
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = ? AND c.relname = ?
                        """,
                PUBLIC_SCHEMA, TARGET).get(0, String.class);
        for (String pair : options.split(",")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return parts[1];
            }
        }
        return null;
    }

    private void setParameter(String name, String value) {
        Routines.setTableStorageParameter(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name, value,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

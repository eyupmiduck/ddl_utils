package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code ddl_utils_lib.add_primary_key_using_index} and
 * {@code add_unique_constraint_using_index}: they attach a pre-built unique
 * index as a primary key or unique constraint through
 * {@code ddl_utils_lib.alter_table}.
 */
class UsingIndexConstraintTest extends PostgresTestBase {

    private static final String TARGET = "using_index_target";
    private static final int DDL_LOCK_TIMEOUT = 1000;
    private static final int SLEEP_TIME = 10;
    private static final int STATEMENT_DURATION = 5000;

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int NOT NULL, code text NOT NULL");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Attaches a valid unique index as the primary key.
     */
    @Test
    void addsPrimaryKeyUsingIndex() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (id)");

        addPrimaryKeyUsingIndex("target_pk", "target_pk_idx");

        assertEquals("p", constraintType("target_pk"));
    }

    /**
     * A missing index raises an error.
     */
    @Test
    void rejectsMissingIndexForPrimaryKey() {
        assertSqlState("42704", () -> addPrimaryKeyUsingIndex("target_pk", "missing_idx"));
    }

    /**
     * Attaches a valid unique index as a unique constraint.
     */
    @Test
    void addsUniqueConstraintUsingIndex() {
        dsl.execute("CREATE UNIQUE INDEX target_code_idx ON " + PUBLIC_SCHEMA + "." + TARGET + " (code)");

        addUniqueConstraintUsingIndex("target_code", "target_code_idx");

        assertEquals("u", constraintType("target_code"));
        assertSqlState("23505",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, code) VALUES (1, 'a'), (2, 'a')"));
    }

    /**
     * Rejects null arguments through their domains.
     */
    @Test
    void rejectsNullArguments() {
        assertDomainViolation(() -> addPrimaryKeyUsingIndex(null, "idx"));
        assertDomainViolation(() -> addPrimaryKeyUsingIndex("pk", null));
        assertDomainViolation(() -> addUniqueConstraintUsingIndex(null, "idx"));
        assertDomainViolation(() -> addUniqueConstraintUsingIndex("uq", null));
    }

    private String constraintType(String name) {
        return dsl.fetchOne(
                """
                        SELECT contype::text
                        FROM pg_constraint
                        WHERE conname = ? AND connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                        """,
                name, PUBLIC_SCHEMA).get(0, String.class);
    }

    private void addPrimaryKeyUsingIndex(String name, String index) {
        Routines.addPrimaryKeyUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name, index,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void addUniqueConstraintUsingIndex(String name, String index) {
        Routines.addUniqueConstraintUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, TARGET, name, index,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies {@code ddl_utils_lib.add_primary_key_using_index} and
 * {@code add_unique_constraint_using_index}: they attach a pre-built unique
 * index as a primary key or unique constraint through
 * {@code ddl_utils_lib.alter_table}.
 */
class UsingIndexConstraintTest extends SingleTableTest {

    UsingIndexConstraintTest() {
        super("using_index_target", "id int NOT NULL, code text NOT NULL");
    }

    /**
     * Attaches a valid unique index as the primary key.
     */
    @Test
    void addsPrimaryKeyUsingIndex() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        addPrimaryKeyUsingIndex("target_pk", "target_pk_idx");

        assertEquals("p", constraintType(PUBLIC_SCHEMA, "target_pk"));
        assertConstraintUsesIndex("target_pk", "id");
    }

    /**
     * A missing index raises an error.
     */
    @Test
    void rejectsMissingIndexForPrimaryKey() {
        assertSqlState("42704", () -> addPrimaryKeyUsingIndex("target_pk", "missing_idx"));
    }

    /**
     * A non-unique index, a partial index and an expression index are all
     * rejected with the same invalid-object-state error, so an incompatible
     * index is never attached.
     */
    @Test
    void rejectsIncompatibleIndexes() {
        dsl.execute("CREATE INDEX target_non_unique_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");
        assertSqlState("42809", () -> addPrimaryKeyUsingIndex("target_pk", "target_non_unique_idx"));

        dsl.execute("CREATE UNIQUE INDEX target_partial_idx ON " + PUBLIC_SCHEMA + "." + target()
                + " (id) WHERE code IS NOT NULL");
        assertSqlState("42809", () -> addUniqueConstraintUsingIndex("target_partial", "target_partial_idx"));

        dsl.execute("CREATE UNIQUE INDEX target_expr_idx ON " + PUBLIC_SCHEMA + "." + target() + " ((id + 1))");
        assertSqlState("42809", () -> addUniqueConstraintUsingIndex("target_expr", "target_expr_idx"));
    }

    /**
     * Attaches a valid unique index as a unique constraint.
     */
    @Test
    void addsUniqueConstraintUsingIndex() {
        dsl.execute("CREATE UNIQUE INDEX target_code_idx ON " + PUBLIC_SCHEMA + "." + target() + " (code)");

        addUniqueConstraintUsingIndex("target_code", "target_code_idx");

        assertEquals("u", constraintType(PUBLIC_SCHEMA, "target_code"));
        assertConstraintUsesIndex("target_code", "code");
        assertSqlState("23505",
                () -> dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, code) VALUES (1, 'a'), (2, 'a')"));
    }

    /**
     * A missing index raises the same error for the unique wrapper.
     */
    @Test
    void rejectsMissingIndexForUniqueConstraint() {
        assertSqlState("42704", () -> addUniqueConstraintUsingIndex("target_code", "missing_idx"));
    }

    /**
     * The attached constraint is on the target table and is backed by an index
     * over exactly the expected column. PostgreSQL renames the backing index to
     * the constraint name.
     */
    private void assertConstraintUsesIndex(String constraint, String column) {
        Record row = dsl.fetchOne("""
                SELECT c.relname AS table_name, ci.relname AS index_name
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_class ci ON ci.oid = con.conindid
                JOIN pg_namespace n ON n.oid = con.connamespace
                WHERE con.conname = ? AND n.nspname = ?
                """, constraint, PUBLIC_SCHEMA);
        assertNotNull(row, () -> "constraint not found: " + constraint);
        assertEquals(target(), row.get("table_name", String.class));
        assertEquals(constraint, row.get("index_name", String.class),
                "USING INDEX renames the backing index to the constraint name");

        List<String> columns = dsl.fetch("""
                SELECT a.attname
                FROM pg_constraint con
                JOIN pg_index i ON i.indexrelid = con.conindid
                JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = ANY (i.indkey)
                WHERE con.conname = ?
                    AND con.connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)
                """, constraint, PUBLIC_SCHEMA).getValues(0, String.class);
        assertEquals(List.of(column), columns);
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

    private void addPrimaryKeyUsingIndex(String name, String index) {
        Routines.addPrimaryKeyUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, target(), name, index,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }

    private void addUniqueConstraintUsingIndex(String name, String index) {
        Routines.addUniqueConstraintUsingIndex(dsl.configuration(), PUBLIC_SCHEMA, target(), name, index,
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION);
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * A non-unique index is rejected by both wrappers with the same
     * invalid-object-state error, so an incompatible index is never attached.
     */
    @Test
    void rejectsNonUniqueIndex() {
        dsl.execute("CREATE INDEX target_non_unique_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        assertSqlState("42809", () -> addPrimaryKeyUsingIndex("target_pk", "target_non_unique_idx"));
        assertSqlState("42809", () -> addUniqueConstraintUsingIndex(
                "target_non_unique", "target_non_unique_idx"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_pk"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_non_unique"));
    }

    /**
     * A partial unique index is rejected.
     */
    @Test
    void rejectsPartialIndex() {
        dsl.execute("CREATE UNIQUE INDEX target_partial_idx ON " + PUBLIC_SCHEMA + "." + target()
                + " (id) WHERE code IS NOT NULL");

        assertSqlState("42809", () -> addUniqueConstraintUsingIndex("target_partial", "target_partial_idx"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_partial"));
    }

    /**
     * A unique expression index is rejected.
     */
    @Test
    void rejectsExpressionIndex() {
        dsl.execute("CREATE UNIQUE INDEX target_expr_idx ON " + PUBLIC_SCHEMA + "." + target() + " ((id + 1))");

        assertSqlState("42809", () -> addUniqueConstraintUsingIndex("target_expr", "target_expr_idx"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_expr"));
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

        // ON CONFLICT ON CONSTRAINT requires the named constraint itself; a
        // mere unique index would not satisfy it.
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target()
                + " (id, code) VALUES (1, 'a'), (2, 'a') ON CONFLICT ON CONSTRAINT target_code DO NOTHING");
        assertEquals(1, dsl.fetchOne("SELECT count(*)::int FROM " + PUBLIC_SCHEMA + "." + target())
                .get(0, Integer.class));
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
     * A primary-key attach requires every indexed column to be NOT NULL; a
     * valid unique index on a nullable column is rejected before the scan.
     */
    @Test
    void rejectsPrimaryKeyOnNullableColumn() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target() + " ADD COLUMN note text");
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + target() + " (note)");

        assertSqlState("55000", () -> addPrimaryKeyUsingIndex("target_note_pk", "target_note_idx"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_note_pk"));
    }

    /**
     * A unique constraint may be attached to a nullable column; only the
     * primary-key path requires NOT NULL.
     */
    @Test
    void acceptsUniqueConstraintOnNullableColumn() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target() + " ADD COLUMN note text");
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + target() + " (note)");

        addUniqueConstraintUsingIndex("target_note", "target_note_idx");

        assertEquals("u", constraintType(PUBLIC_SCHEMA, "target_note"));
    }

    /**
     * A qualified or pre-quoted index name is rejected: USING INDEX resolves an
     * unqualified name in the table's schema.
     */
    @Test
    void rejectsQualifiedIndexName() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        assertSqlState("22023", () -> addPrimaryKeyUsingIndex("target_pk", PUBLIC_SCHEMA + ".target_pk_idx"));
    }

    /**
     * Over-long constraint and index names are rejected before PostgreSQL
     * truncates them.
     */
    @Test
    void rejectsOverLongNames() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        assertSqlState("22023", () -> addPrimaryKeyUsingIndex("c".repeat(64), "target_pk_idx"));
        assertSqlState("22023", () -> addPrimaryKeyUsingIndex("target_pk", "i".repeat(64)));
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

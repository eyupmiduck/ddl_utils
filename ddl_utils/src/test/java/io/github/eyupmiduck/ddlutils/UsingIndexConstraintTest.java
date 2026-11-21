package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
     * over exactly the expected columns, in index order. PostgreSQL renames the
     * backing index to the constraint name.
     *
     * @param constraint the constraint name
     * @param columns    the expected key columns, in index order
     */
    private void assertConstraintUsesIndex(String constraint, String... columns) {
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

        // Walk indkey positionally and stop at indnkeyatts: INCLUDE columns sit
        // after the key columns in indkey and are not part of the constraint.
        List<String> indexed = dsl.fetch("""
                SELECT a.attname
                FROM pg_constraint con
                JOIN pg_index i ON i.indexrelid = con.conindid
                CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS key_column(attnum, position)
                JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = key_column.attnum
                WHERE con.conname = ?
                    AND con.conrelid = pg_catalog.format('%I.%I', ?, ?)::regclass
                    AND key_column.position <= i.indnkeyatts
                ORDER BY key_column.position
                """, constraint, PUBLIC_SCHEMA, target()).getValues(0, String.class);
        assertEquals(List.of(columns), indexed);
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
     * The indexed-column assertion reads the key columns in index order and
     * excludes INCLUDE columns, so a multi-column index with an included column
     * is not mistaken for a three-column constraint or read in arbitrary catalog
     * order.
     */
    @Test
    void attachesMultiColumnIndexWithAnIncludedColumn() {
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target() + " ADD COLUMN note text");
        dsl.execute("CREATE UNIQUE INDEX target_multi_idx ON " + PUBLIC_SCHEMA + "." + target()
                + " (code, id) INCLUDE (note)");

        addUniqueConstraintUsingIndex("target_multi", "target_multi_idx");

        assertEquals("u", constraintType(PUBLIC_SCHEMA, "target_multi"));
        assertConstraintUsesIndex("target_multi", "code", "id");
    }

    /**
     * A qualified or pre-quoted index name is rejected by both wrappers: USING
     * INDEX resolves an unqualified name in the table's schema.
     */
    @Test
    void rejectsQualifiedIndexName() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        assertSqlState("22023", () -> addPrimaryKeyUsingIndex("target_pk", PUBLIC_SCHEMA + ".target_pk_idx"));
        assertSqlState("22023", () -> addUniqueConstraintUsingIndex("target_uq", PUBLIC_SCHEMA + ".target_pk_idx"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_pk"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_uq"));
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
     * A missing table is reported as a controlled undefined_table error rather
     * than a raw ::regclass cast failure.
     */
    @Test
    void rejectsMissingTable() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        assertSqlState("42P01", () -> Routines.addUniqueConstraintUsingIndex(
                dsl.configuration(), PUBLIC_SCHEMA, "using_index_missing_table", "uq", "target_pk_idx",
                DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
    }

    /**
     * An index that exists but backs another table is reported as "not on
     * table", not as an invalid unique index.
     */
    @Test
    void rejectsIndexOnAnotherTable() {
        dsl.execute("CREATE TABLE " + PUBLIC_SCHEMA + ".using_index_other (id int NOT NULL)");
        try {
            dsl.execute("CREATE UNIQUE INDEX using_index_other_idx ON "
                    + PUBLIC_SCHEMA + ".using_index_other (id)");

            DataAccessException failure = assertThrows(DataAccessException.class,
                    () -> addUniqueConstraintUsingIndex("uq", "using_index_other_idx"));

            assertEquals("42809", failure.sqlState());
            assertTrue(failure.getMessage().contains("is not on table"),
                    () -> "the message should name the wrong table: " + failure.getMessage());
        } finally {
            dsl.execute("DROP TABLE IF EXISTS " + PUBLIC_SCHEMA + ".using_index_other");
        }
    }

    /**
     * A non-table relation is reported as "is not a table" rather than as a
     * missing/misconfigured index.
     */
    @Test
    void rejectsNonTableRelation() {
        dsl.execute("CREATE VIEW " + PUBLIC_SCHEMA + ".using_index_view AS SELECT 1 AS id");
        try {
            assertSqlState("42809", () -> Routines.addUniqueConstraintUsingIndex(
                    dsl.configuration(), PUBLIC_SCHEMA, "using_index_view", "uq", "missing_idx",
                    DDL_LOCK_TIMEOUT, SLEEP_TIME, STATEMENT_DURATION));
        } finally {
            dsl.execute("DROP VIEW IF EXISTS " + PUBLIC_SCHEMA + ".using_index_view");
        }
    }

    /**
     * A pre-quoted index name is rejected by both wrappers: USING INDEX takes an
     * unquoted identifier.
     */
    @Test
    void rejectsPreQuotedIndexName() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        assertSqlState("22023", () -> addPrimaryKeyUsingIndex("target_pk", "\"target_pk_idx\""));
        assertSqlState("22023", () -> addUniqueConstraintUsingIndex("target_uq", "\"target_pk_idx\""));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_pk"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_uq"));
    }

    /**
     * A partitioned index cannot be attached by PostgreSQL, so it is reported as
     * unsupported instead of "index does not exist".
     */
    @Test
    void rejectsPartitionedIndex() {
        dsl.execute("CREATE TABLE " + PUBLIC_SCHEMA
                + ".using_index_partitioned (id int NOT NULL) PARTITION BY RANGE (id)");
        try {
            dsl.execute("CREATE UNIQUE INDEX using_index_partitioned_idx ON "
                    + PUBLIC_SCHEMA + ".using_index_partitioned (id)");

            assertSqlState("0A000", () -> addUniqueConstraintUsingIndex(
                    "uq", "using_index_partitioned_idx"));
        } finally {
            dsl.execute("DROP TABLE IF EXISTS " + PUBLIC_SCHEMA + ".using_index_partitioned");
        }
    }

    /**
     * Exactly 63 bytes (NAMEDATALEN - 1) is accepted for both the constraint and
     * the index name.
     */
    @Test
    void acceptsNamesAtTheLegalBoundary() {
        dsl.execute("CREATE UNIQUE INDEX target_pk_idx ON " + PUBLIC_SCHEMA + "." + target() + " (id)");
        String longIndex = "i".repeat(63);
        dsl.execute("CREATE UNIQUE INDEX " + longIndex + " ON " + PUBLIC_SCHEMA + "." + target() + " (code)");

        String constraint = "c".repeat(63);
        addPrimaryKeyUsingIndex(constraint, "target_pk_idx");
        assertEquals("p", constraintType(PUBLIC_SCHEMA, constraint));

        addUniqueConstraintUsingIndex("target_code", longIndex);
        assertEquals("u", constraintType(PUBLIC_SCHEMA, "target_code"));
    }

    /**
     * The primary-key helper accepts a 63-byte index name, pinning the length
     * guard boundary on the index side of that helper too.
     */
    @Test
    void acceptsPrimaryKeyIndexNameAtTheLegalBoundary() {
        String longIndex = "i".repeat(63);
        dsl.execute("CREATE UNIQUE INDEX " + longIndex + " ON " + PUBLIC_SCHEMA + "." + target() + " (id)");

        addPrimaryKeyUsingIndex("target_pk", longIndex);

        assertEquals("p", constraintType(PUBLIC_SCHEMA, "target_pk"));
        assertConstraintUsesIndex("target_pk", "id");
    }

    /**
     * The unique-constraint helper accepts a 63-byte constraint name, pinning
     * the length guard boundary on the constraint side of that helper too.
     */
    @Test
    void acceptsUniqueConstraintNameAtTheLegalBoundary() {
        String longConstraint = "c".repeat(63);
        dsl.execute("CREATE UNIQUE INDEX target_code_idx ON " + PUBLIC_SCHEMA + "." + target() + " (code)");

        addUniqueConstraintUsingIndex(longConstraint, "target_code_idx");

        assertEquals("u", constraintType(PUBLIC_SCHEMA, longConstraint));
        assertConstraintUsesIndex(longConstraint, "code");
    }

    /**
     * On PostgreSQL 18+ a catalogued NOT NULL constraint can be NOT VALID while
     * the column's attnotnull is already true; the primary-key attach must
     * report that clearly instead of failing inside ALTER TABLE.
     */
    @Test
    void rejectsUnvalidatedNotNullConstraint() {
        Integer serverVersion = dsl.fetchOne("SELECT current_setting('server_version_num')::int")
                .get(0, Integer.class);
        Assumptions.assumeTrue(serverVersion != null && serverVersion >= 180000,
                "NOT VALID NOT NULL constraints need PostgreSQL 18+");

        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target() + " ADD COLUMN note text");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD CONSTRAINT target_note_nn NOT NULL note NOT VALID");
        dsl.execute("CREATE UNIQUE INDEX target_note_idx ON " + PUBLIC_SCHEMA + "." + target() + " (note)");

        assertSqlState("55000", () -> addPrimaryKeyUsingIndex("target_note_pk", "target_note_idx"));
        assertFalse(constraintExists(PUBLIC_SCHEMA, target(), "target_note_pk"));
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

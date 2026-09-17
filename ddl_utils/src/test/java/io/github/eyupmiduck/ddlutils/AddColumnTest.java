package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.Routines;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils.add_column}: it builds an ADD COLUMN fragment and
 * applies it through {@code ddl_utils.alter_table}, honouring the nullable flag
 * and an optional default expression.
 */
class AddColumnTest extends PostgresTestBase {

    private static final String SCHEMA = "public";
    private static final String TARGET = "add_column_target";

    @BeforeEach
    void createTargetTable() {
        dsl.execute("CREATE TABLE " + TARGET + " (id int)");
    }

    @AfterEach
    void dropTargetTable() {
        dsl.execute("DROP TABLE IF EXISTS " + TARGET);
    }

    /**
     * Adds a nullable column with no default; the column accepts nulls and has
     * no default.
     */
    @Test
    void addsNullableColumnWithoutDefault() {
        addColumn("note", "text", null, true, 1000, 10, 5000);

        assertNotNull(column("note"));
        assertEquals("YES", columnAttribute("note", "is_nullable"));
        assertNull(columnAttribute("note", "column_default"));
    }

    /**
     * Adds a column with a default SQL expression, which becomes the column
     * default.
     */
    @Test
    void addsColumnWithDefaultExpression() {
        addColumn("created", "timestamptz", "now()", true, 1000, 10, 5000);

        assertTrue(columnAttribute("created", "column_default").contains("now()"));
    }

    /**
     * Adds a NOT NULL column with a literal default.
     */
    @Test
    void addsNotNullColumnWithDefault() {
        addColumn("count", "int", "0", false, 1000, 10, 5000);

        assertEquals("NO", columnAttribute("count", "is_nullable"));
        assertEquals("0", columnAttribute("count", "column_default"));
    }

    /**
     * Rejects null for each mandatory text argument through its domain.
     */
    @Test
    void rejectsNullTextArguments() {
        assertDomainViolation(() -> addColumn(null, "text", null, true, 100, 100, 1000));
        assertDomainViolation(() -> addColumn("note", null, null, true, 100, 100, 1000));
    }

    /**
     * Rejects null for the nullable flag and null or negative values for the
     * integer arguments through their domains.
     */
    @Test
    void rejectsNullAndNegativeArguments() {
        assertDomainViolation(() -> addColumn("note", "text", null, null, 100, 100, 1000));
        assertDomainViolation(() -> addColumn("note", "text", null, true, null, 100, 1000));
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, null, 1000));
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, 100, null));
        assertDomainViolation(() -> addColumn("note", "text", null, true, -1, 100, 1000));
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, -1, 1000));
        assertDomainViolation(() -> addColumn("note", "text", null, true, 100, 100, -1));
    }

    private void addColumn(String column, String type, String defaultValue, Boolean nullable,
                           Integer lockTimeout, Integer sleepTime, Integer duration) {
        Routines.addColumn(dsl.configuration(), SCHEMA, TARGET, column, type, defaultValue,
                nullable, lockTimeout, sleepTime, duration);
    }

    private Record column(String name) {
        return dsl.fetchOne(
                """
                SELECT is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """,
                SCHEMA, TARGET, name);
    }

    private String columnAttribute(String name, String attribute) {
        Record record = column(name);
        assertNotNull(record, () -> "column not found: " + name);
        return record.get(attribute, String.class);
    }

    private static void assertDomainViolation(Executable call) {
        DataAccessException exception = assertThrows(DataAccessException.class, call);
        assertEquals("23514", sqlState(exception),
                () -> "expected a domain check violation but was: " + exception.getMessage());
    }
}

package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the {@code ddl_utils.ensure_not_null} procedure: it makes a column
 * {@code NOT NULL} through the scan-avoiding sequence (add a NOT VALID check,
 * validate it, set NOT NULL, drop the temporary constraint), committing between
 * steps, and recovers from a partial failure when called again.
 */
class EnsureNotNullProcedureTest extends PostgresTestBase {

    private static final String TARGET = "ensure_not_null_procedure_target";

    @BeforeEach
    void createTargetTable() {
        createTestTable(TARGET, "id int, note text");
    }

    @AfterEach
    void dropTargetTable() {
        dropTestTable(TARGET);
    }

    /**
     * Makes a nullable column NOT NULL and removes the temporary constraint.
     */
    @Test
    void makesColumnNotNull() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, 'a')");

        callEnsureNotNull();

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals(0, temporaryConstraints());
    }

    /**
     * A second call on an already NOT NULL column is a no-op and still leaves no
     * temporary constraint behind.
     */
    @Test
    void isIdempotent() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, 'a')");
        callEnsureNotNull();

        callEnsureNotNull();

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals(0, temporaryConstraints());
    }

    /**
     * A call interrupted after the first step (the NOT VALID constraint was
     * committed but validation had not run) is completed by calling again.
     */
    @Test
    void recoversFromPartialFailureAfterAdd() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, 'a')");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT " + temporaryConstraintName() + " CHECK (note IS NOT NULL) NOT VALID");

        callEnsureNotNull();

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals(0, temporaryConstraints());
    }

    /**
     * A call interrupted after the constraint was validated (NOT NULL not yet
     * set) is completed by calling again.
     */
    @Test
    void recoversFromPartialFailureAfterValidate() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, 'a')");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT " + temporaryConstraintName() + " CHECK (note IS NOT NULL) NOT VALID");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " VALIDATE CONSTRAINT " + temporaryConstraintName());

        callEnsureNotNull();

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals(0, temporaryConstraints());
    }

    /**
     * Existing NULLs make validation fail with a check violation; the temporary
     * constraint is left in place as NOT VALID, and the same call succeeds after
     * the data is fixed.
     */
    @Test
    void failsOnExistingNullThenSucceedsAfterFix() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, NULL)");

        assertSqlState("23514", this::callEnsureNotNull);

        assertEquals("YES", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals(1, temporaryConstraints());

        dsl.execute("UPDATE " + PUBLIC_SCHEMA + "." + TARGET + " SET note = 'fixed' WHERE note IS NULL");
        callEnsureNotNull();

        assertEquals("NO", columnAttribute(PUBLIC_SCHEMA, TARGET, "note", "is_nullable"));
        assertEquals(0, temporaryConstraints());
    }

    /**
     * The procedure enforces NOT NULL for future rows.
     */
    @Test
    void enforcesNotNullForNewRows() {
        callEnsureNotNull();

        assertSqlState("23502", () -> dsl.execute(
                "INSERT INTO " + PUBLIC_SCHEMA + "." + TARGET + " (id, note) VALUES (1, NULL)"));
    }

    private void callEnsureNotNull() {
        dsl.execute("CALL ddl_utils.ensure_not_null(?, ?, ?)", PUBLIC_SCHEMA, TARGET, "note");
    }

    private String temporaryConstraintName() {
        String digest = dsl.fetchOne("SELECT substr(md5(? || '.' || ? || '.' || ?), 1, 8)",
                PUBLIC_SCHEMA, TARGET, "note").get(0, String.class);
        String prefix = TARGET + "_note_not_null";
        if (prefix.length() > 45) {
            prefix = prefix.substring(0, 45);
        }
        return prefix + "_" + digest;
    }

    private int temporaryConstraints() {
        Integer count = dsl.fetchOne(
                """
                        SELECT count(*)::int
                        FROM pg_constraint
                        WHERE conrelid = (SELECT oid FROM pg_class WHERE relname = ?
                                            AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?))
                        """,
                TARGET, PUBLIC_SCHEMA).get(0, Integer.class);
        return count != null ? count : -1;
    }
}

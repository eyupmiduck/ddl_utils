package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code ddl_utils.ensure_not_null} procedure: it makes a column
 * {@code NOT NULL} through the scan-avoiding sequence (add a NOT VALID check,
 * validate it, set NOT NULL, drop the temporary constraint), committing between
 * steps, and recovers from a partial failure when called again.
 */
class EnsureNotNullProcedureTest extends SingleTableTest {

    EnsureNotNullProcedureTest() {
        super("ensure_not_null_procedure_target", "id int, note text");
    }

    /**
     * Makes a nullable column NOT NULL and removes the temporary constraint.
     */
    @Test
    void makesColumnNotNull() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");

        callEnsureNotNull();

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * A second call on an already NOT NULL column is a no-op and still leaves no
     * temporary constraint behind.
     */
    @Test
    void isIdempotent() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");
        callEnsureNotNull();

        callEnsureNotNull();

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * A call on an already NOT NULL column is a true no-op: it performs no
     * ALTER TABLE, so it succeeds even while another session holds a lock that
     * every ALTER TABLE would have to wait for.
     */
    @Test
    void isNoOpWhenAlreadyNotNull() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");
        callEnsureNotNull();

        setTableLockSettings(PUBLIC_SCHEMA, target(), 100, 100, 300);
        try {
            runWhileTableLocked(target(), 5000, this::callEnsureNotNull);
        } finally {
            clearTableLockSettings(PUBLIC_SCHEMA, target());
        }

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * A multibyte column name still yields a generated constraint name short
     * enough for PostgreSQL, so a second call finds the temporary constraint
     * instead of failing on a name that was silently truncated.
     */
    @Test
    void isIdempotentWithMultibyteColumnName() {
        String column = "\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9";
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD COLUMN \"" + column + "\" text");
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, \"" + column + "\") VALUES (1, 'a')");

        callEnsureNotNull(column);
        callEnsureNotNull(column);

        assertNotNullable(PUBLIC_SCHEMA, target(), column);
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), column));
    }

    /**
     * A partial failure on a multibyte column name is recovered: the quoted,
     * NOT VALID proof left by step 1 is recognised as this routine's proof and
     * validated, not mistaken for a foreign constraint or lost to identifier
     * truncation.
     */
    @Test
    void recoversFromPartialFailureWithMultibyteColumnName() {
        String column = "\u00e9".repeat(12);
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD COLUMN \"" + column + "\" text");
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target()
                + " (id, \"" + column + "\") VALUES (1, 'a')");

        simulateNotNullCheckAdded(PUBLIC_SCHEMA, target(), column);
        assertNotValidated(PUBLIC_SCHEMA, target(),
                notNullCheckConstraintName(PUBLIC_SCHEMA, target(), column));

        callEnsureNotNull(column);

        assertNotNullable(PUBLIC_SCHEMA, target(), column);
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), column));
    }

    /**
     * Concurrent callers converge on a NOT NULL column with no temporary proof
     * left behind: every step re-reads the catalog after re-acquiring the
     * advisory lock, so a caller cannot act on state another caller changed.
     */
    @Test
    void concurrentCallsConverge() throws Exception {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");

        int callers = 4;
        CyclicBarrier barrier = new CyclicBarrier(callers);
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try (Connection connection = openTestConnection();
                         PreparedStatement statement = connection.prepareStatement(
                                 "CALL ddl_utils.ensure_not_null(?, ?, ?)")) {
                        statement.setString(1, PUBLIC_SCHEMA);
                        statement.setString(2, target());
                        statement.setString(3, "note");
                        statement.execute();
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * A call interrupted after the first step (the NOT VALID constraint was
     * committed but validation had not run) is completed by calling again.
     */
    @Test
    void recoversFromPartialFailureAfterAdd() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");
        simulateNotNullCheckAdded(PUBLIC_SCHEMA, target(), "note");

        callEnsureNotNull();

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * A call interrupted after the constraint was validated (NOT NULL not yet
     * set) is completed by calling again.
     */
    @Test
    void recoversFromPartialFailureAfterValidate() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");
        simulateNotNullCheckValidated(PUBLIC_SCHEMA, target(), "note");

        callEnsureNotNull();

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * Existing NULLs make validation fail with a check violation; the temporary
     * constraint is left in place as NOT VALID, and the same call succeeds after
     * the data is fixed.
     */
    @Test
    void failsOnExistingNullThenSucceedsAfterFix() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, NULL)");

        assertSqlState("23514", this::callEnsureNotNull);

        assertNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(1, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));

        dsl.execute("UPDATE " + PUBLIC_SCHEMA + "." + target() + " SET note = 'fixed' WHERE note IS NULL");
        callEnsureNotNull();

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * The procedure enforces NOT NULL for future rows.
     */
    @Test
    void enforcesNotNullForNewRows() {
        callEnsureNotNull();

        assertSqlState("23502", () -> dsl.execute(
                "INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, NULL)"));
    }

    /**
     * An unknown column is rejected with undefined_column; the procedure never
     * silently succeeds with a NULL state flag.
     */
    @Test
    void rejectsUnknownColumn() {
        assertSqlState("42703", () -> callEnsureNotNull("missing"));
    }

    /**
     * A pre-existing constraint that happens to have the generated proof name
     * but a different definition is not treated as this routine's proof: the
     * call fails and the foreign constraint is left in place.
     */
    @Test
    void rejectsSameNamedConstraintWithDifferentDefinition() {
        String constraint = notNullCheckConstraintName(PUBLIC_SCHEMA, target(), "note");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target()
                + " ADD CONSTRAINT " + constraint + " CHECK (note <> '')");

        assertSqlState("42710", this::callEnsureNotNull);

        assertTrue(constraintExists(PUBLIC_SCHEMA, target(), constraint),
                "the pre-existing constraint must not be dropped");
        assertEquals(1, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    private void callEnsureNotNull() {
        callEnsureNotNull("note");
    }

    private void callEnsureNotNull(String column) {
        dsl.execute("CALL ddl_utils.ensure_not_null(?, ?, ?)", PUBLIC_SCHEMA, target(), column);
    }
}

package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies the {@code ddl_utils.ensure_not_null} procedure: it makes a column
 * {@code NOT NULL} through the scan-avoiding sequence (add a NOT VALID check,
 * validate it, set NOT NULL, drop the temporary constraint), committing between
 * steps, and recovers from a partial failure when called again.
 */
class EnsureNotNullProcedureTest extends SingleTableTest {

    /**
     * The server-side statement timeout each concurrent worker sets, so a CALL
     * blocked on a lock aborts instead of hanging the build.
     */
    private static final String WORKER_STATEMENT_TIMEOUT = "15s";

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
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            tasks.add(() -> {
                try (Connection connection = openWorkerConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "CALL ddl_utils.ensure_not_null(?, ?, ?)")) {
                    statement.setString(1, PUBLIC_SCHEMA);
                    statement.setString(2, target());
                    statement.setString(3, "note");
                    barrier.await(10, TimeUnit.SECONDS);
                    statement.execute();
                }
                return null;
            });
        }

        runConcurrently(callers, tasks);

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
        assertNotValidated(PUBLIC_SCHEMA, target(),
                notNullCheckConstraintName(PUBLIC_SCHEMA, target(), "note"));

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
        assertValidated(PUBLIC_SCHEMA, target(),
                notNullCheckConstraintName(PUBLIC_SCHEMA, target(), "note"));

        callEnsureNotNull();

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * A call interrupted after {@code SET NOT NULL} was committed but before the
     * temporary proof was dropped leaves the column NOT NULL with the proof
     * still present; calling again recognizes it and removes it.
     */
    @Test
    void recoversFromPartialFailureAfterSetNotNull() {
        dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + target() + " (id, note) VALUES (1, 'a')");
        simulateNotNullCheckValidated(PUBLIC_SCHEMA, target(), "note");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + target() + " ALTER COLUMN note SET NOT NULL");
        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(1, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"),
                "the interrupted state must have the proof constraint still present");

        callEnsureNotNull();

        assertNotNullable(PUBLIC_SCHEMA, target(), "note");
        assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, target(), "note"));
    }

    /**
     * A partitioned parent is accepted and PostgreSQL recurses each step: the
     * parent and every partition end NOT NULL, no temporary proof is left on
     * either, and a second call is a no-op.
     */
    @Test
    void handlesPartitionedParent() {
        String parent = "ensure_not_null_partitioned";
        String partition = "ensure_not_null_partitioned_1";
        dsl.execute("CREATE TABLE " + PUBLIC_SCHEMA + "." + parent
                + " (id int, note text) PARTITION BY RANGE (id)");
        try {
            dsl.execute("CREATE TABLE " + PUBLIC_SCHEMA + "." + partition
                    + " PARTITION OF " + PUBLIC_SCHEMA + "." + parent + " FOR VALUES FROM (0) TO (100)");
            dsl.execute("INSERT INTO " + PUBLIC_SCHEMA + "." + parent + " (id, note) VALUES (1, 'a')");

            callEnsureNotNull(parent, "note");

            assertNotNullable(PUBLIC_SCHEMA, parent, "note");
            assertNotNullable(PUBLIC_SCHEMA, partition, "note");
            assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, parent, "note"));
            assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, partition, "note"));

            callEnsureNotNull(parent, "note");

            assertNotNullable(PUBLIC_SCHEMA, parent, "note");
            assertNotNullable(PUBLIC_SCHEMA, partition, "note");
            assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, parent, "note"));
            assertEquals(0, notNullProofConstraintCount(PUBLIC_SCHEMA, partition, "note"));
        } finally {
            dsl.execute("DROP TABLE IF EXISTS " + PUBLIC_SCHEMA + "." + parent);
        }
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
        assertNotValidated(PUBLIC_SCHEMA, target(),
                notNullCheckConstraintName(PUBLIC_SCHEMA, target(), "note"));

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
     * A view is not a table; the relation-kind guard rejects it before any DDL.
     */
    @Test
    void rejectsNonTableRelation() {
        dsl.execute("CREATE VIEW " + PUBLIC_SCHEMA + ".ensure_not_null_view AS SELECT 1 AS note");
        try {
            assertSqlState("42809", () -> dsl.execute(
                    "CALL ddl_utils.ensure_not_null(?, ?, ?)",
                    PUBLIC_SCHEMA, "ensure_not_null_view", "note"));
        } finally {
            dsl.execute("DROP VIEW IF EXISTS " + PUBLIC_SCHEMA + ".ensure_not_null_view");
        }
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

    /**
     * All three identifiers are bounded at 63 bytes before any catalog lookup or
     * DDL: an over-long schema, table or column is rejected with 22023, while a
     * 63-byte missing column passes the guard and fails later as
     * undefined_column (42703).
     */
    @Test
    void boundsIdentifierLength() {
        assertSqlState("22023", () -> dsl.execute("CALL ddl_utils.ensure_not_null(?, ?, ?)",
                "s".repeat(64), target(), "note"));
        assertSqlState("22023", () -> dsl.execute("CALL ddl_utils.ensure_not_null(?, ?, ?)",
                PUBLIC_SCHEMA, "t".repeat(64), "note"));
        assertSqlState("22023", () -> callEnsureNotNull("c".repeat(64)));
        assertSqlState("42703", () -> callEnsureNotNull("c".repeat(63)));
    }

    private void callEnsureNotNull() {
        callEnsureNotNull("note");
    }

    private void callEnsureNotNull(String column) {
        callEnsureNotNull(target(), column);
    }

    private void callEnsureNotNull(String table, String column) {
        dsl.execute("CALL ddl_utils.ensure_not_null(?, ?, ?)", PUBLIC_SCHEMA, table, column);
    }

    /**
     * Opens a worker connection whose session aborts any statement after
     * {@link #WORKER_STATEMENT_TIMEOUT}: the backstop for a CALL the driver
     * cannot interrupt once it is blocked in a socket read.
     *
     * @return a connection with a server-side statement timeout
     * @throws SQLException if the connection or the setting fails
     */
    private Connection openWorkerConnection() throws SQLException {
        Connection connection = openTestConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET statement_timeout = '" + WORKER_STATEMENT_TIMEOUT + "'");
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    /**
     * Runs the tasks on a fixed pool and waits at most 30 s for each, cancelling
     * every worker on timeout and always shutting the pool down, so a blocked
     * procedure cannot hang the build.
     *
     * @param workers the pool size
     * @param tasks   the worker tasks
     * @throws Exception the first worker failure, or the timeout failure
     */
    private void runConcurrently(int workers, List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    for (Future<Void> pending : futures) {
                        pending.cancel(true);
                    }
                    fail("a concurrent ensure_not_null call did not finish within 30 s", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

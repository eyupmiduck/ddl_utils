package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;

import java.sql.SQLException;

/**
 * Base for tests that exercise one target table. The subclass supplies the
 * table name and column list; the base creates the table before each test and
 * drops it after, always clearing the table-level lock settings first (the
 * clear is a no-op when the test never wrote them).
 */
abstract class SingleTableTest extends PostgresTestBase {

    private final String targetName;
    private final String columns;
    private boolean tableCreated;

    /**
     * Creates a fixture whose target table is created and dropped around each
     * test.
     *
     * @param targetName the target table name
     * @param columns    the column list for the target table
     */
    protected SingleTableTest(String targetName, String columns) {
        this.targetName = targetName;
        this.columns = columns;
    }

    /**
     * The target table name, for use in calls and assertions.
     *
     * @return the target table name
     */
    protected final String target() {
        return targetName;
    }

    /**
     * Sets the target table's lock settings to a short budget and asserts each
     * call gives up quickly while the table is locked. The table budget is far
     * below the database default (30000 ms), so a wrapper that used the database
     * defaults would retry for ~30 s instead of giving up.
     *
     * @param wrapperCalls the wrapper calls expected to give up
     * @throws SQLException         if the competing connection cannot be opened
     * @throws InterruptedException if waiting for the lock is interrupted
     */
    protected void assertUsesTableLockSettings(Executable... wrapperCalls)
            throws SQLException, InterruptedException {
        assertUsesTableLockSettings(100, 100, 300, 2000, wrapperCalls);
    }

    /**
     * Sets the target table's lock settings and asserts each call gives up
     * quickly while the table is locked.
     *
     * @param lockTimeout       the per-attempt lock timeout in ms
     * @param sleepTime         the retry sleep in ms
     * @param statementDuration the statement budget in ms
     * @param giveUpMillis      the maximum expected time to give up
     * @param wrapperCalls      the wrapper calls expected to give up
     * @throws SQLException         if the competing connection cannot be opened
     * @throws InterruptedException if waiting for the lock is interrupted
     */
    protected void assertUsesTableLockSettings(int lockTimeout, int sleepTime, int statementDuration,
                                               long giveUpMillis, Executable... wrapperCalls) throws SQLException, InterruptedException {
        if (wrapperCalls.length == 0) {
            throw new IllegalArgumentException("at least one wrapper call is required");
        }
        setTableLockSettings(PUBLIC_SCHEMA, targetName, lockTimeout, sleepTime, statementDuration);
        for (Executable wrapperCall : wrapperCalls) {
            assertGivesUpWhileTableLocked(targetName, giveUpMillis, wrapperCall);
        }
    }

    @BeforeEach
    void createTargetTable() {
        createTestTable(targetName, columns);
        tableCreated = true;
    }

    @AfterEach
    void dropTargetTable() {
        // Clear unconditionally: the helper is a no-op when no row exists, and
        // assertUsesTableLockSettings may have written one regardless of how the
        // test was constructed. Preserve the first cleanup failure and attach the
        // second as suppressed so neither hides the other.
        RuntimeException failure = null;
        try {
            clearTableLockSettings(PUBLIC_SCHEMA, targetName);
        } catch (RuntimeException e) {
            failure = e;
        }
        if (tableCreated) {
            try {
                dropTestTable(targetName);
            } catch (RuntimeException e) {
                if (failure != null) {
                    failure.addSuppressed(e);
                } else {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}

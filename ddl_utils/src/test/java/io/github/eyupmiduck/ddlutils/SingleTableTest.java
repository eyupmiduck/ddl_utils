package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;

import java.sql.SQLException;

/**
 * Base for tests that exercise one target table. The subclass supplies the
 * table name and column list; the base creates the table before each test and
 * drops it after, clearing the table-level lock settings first when the
 * subclass sets them.
 */
abstract class SingleTableTest extends PostgresTestBase {

    private final String targetName;
    private final String columns;
    private final boolean clearLockSettings;

    /**
     * Creates a fixture whose target table has no table-level lock settings to
     * clear.
     *
     * @param targetName the target table name
     * @param columns    the column list for the target table
     */
    protected SingleTableTest(String targetName, String columns) {
        this(targetName, columns, false);
    }

    /**
     * Creates a fixture, clearing the target table's lock settings on teardown
     * when {@code clearLockSettings} is set.
     *
     * @param targetName        the target table name
     * @param columns           the column list for the target table
     * @param clearLockSettings whether to clear the table's lock settings on teardown
     */
    protected SingleTableTest(String targetName, String columns, boolean clearLockSettings) {
        this.targetName = targetName;
        this.columns = columns;
        this.clearLockSettings = clearLockSettings;
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
        setTableLockSettings(PUBLIC_SCHEMA, targetName, lockTimeout, sleepTime, statementDuration);
        for (Executable wrapperCall : wrapperCalls) {
            assertGivesUpWhileTableLocked(targetName, giveUpMillis, wrapperCall);
        }
    }

    @BeforeEach
    void createTargetTable() {
        createTestTable(targetName, columns);
    }

    @AfterEach
    void dropTargetTable() {
        if (clearLockSettings) {
            try {
                clearTableLockSettings(PUBLIC_SCHEMA, targetName);
            } finally {
                dropTestTable(targetName);
            }
        } else {
            dropTestTable(targetName);
        }
    }
}

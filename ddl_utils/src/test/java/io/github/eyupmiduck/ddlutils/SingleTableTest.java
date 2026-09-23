package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

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

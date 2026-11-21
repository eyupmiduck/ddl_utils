package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code ddl_utils.move_table_lock_settings}: it moves a table's lock
 * settings to a new name in one statement, reports a replacement only when
 * there is a source row to move, and preserves {@code created_at}.
 */
class MoveTableLockSettingsTest extends PostgresTestBase {

    private static final String OLD_NAME = "move_settings_old";
    private static final String NEW_NAME = "move_settings_new";
    private static final String REPLACEMENT_NOTICE = "replacing the existing lock settings";

    @AfterEach
    void clearSettings() {
        clearTableLockSettings(PUBLIC_SCHEMA, OLD_NAME);
        clearTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
    }

    /**
     * When the source row exists and the destination has none, the values move
     * under the new name, the source row is deleted and the source's
     * {@code created_at} is carried across. Nothing is replaced, so no
     * replacement NOTICE is emitted.
     */
    @Test
    void movesSettingsAndPreservesCreatedAt() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, OLD_NAME, 10, 20, 30);
        setCreatedAt(OLD_NAME, "2020-01-01 00:00:00+00");

        List<String> warnings = moveAndCollectWarnings(OLD_NAME, NEW_NAME);

        assertNull(getTableLockSettings(PUBLIC_SCHEMA, OLD_NAME),
                "the source row must be deleted by the move");
        Record settings = getTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
        assertLockSettings(settings, 10, 20, 30);
        assertTrue(createdAtIs(NEW_NAME, "2020-01-01 00:00:00+00"),
                "the moved row must keep the source's created_at");
        assertTrue(warnings.stream().noneMatch(message -> message.contains(REPLACEMENT_NOTICE)),
                () -> "a move with no destination row is not a replacement: " + warnings);
    }

    /**
     * A missing source row makes the move a no-op: a destination row that was
     * pre-provisioned keeps its values and {@code created_at}, no row appears
     * under the source name, and no replacement NOTICE is emitted.
     */
    @Test
    void missingSourceLeavesExistingDestinationUntouched() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, NEW_NAME, 100, 200, 300);
        setCreatedAt(NEW_NAME, "2010-01-01 00:00:00+00");

        List<String> warnings = moveAndCollectWarnings(OLD_NAME, NEW_NAME);

        assertNull(getTableLockSettings(PUBLIC_SCHEMA, OLD_NAME),
                "a no-op move must not create a source row");
        Record settings = getTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
        assertLockSettings(settings, 100, 200, 300);
        assertTrue(createdAtIs(NEW_NAME, "2010-01-01 00:00:00+00"),
                "the pre-existing destination row must be untouched");
        assertTrue(warnings.stream().noneMatch(message -> message.contains(REPLACEMENT_NOTICE)),
                () -> "no replacement happened, so no NOTICE may be emitted: " + warnings);
    }

    /**
     * With neither a source nor a destination row the move is a no-op, so it
     * creates no row and emits no replacement NOTICE. This is the common case
     * for a rename of a table that has no override.
     */
    @Test
    void missingSourceAndDestinationCreatesNothing() throws Exception {
        List<String> warnings = moveAndCollectWarnings(OLD_NAME, NEW_NAME);

        assertNull(getTableLockSettings(PUBLIC_SCHEMA, OLD_NAME));
        assertNull(getTableLockSettings(PUBLIC_SCHEMA, NEW_NAME));
        assertTrue(warnings.stream().noneMatch(message -> message.contains(REPLACEMENT_NOTICE)),
                () -> "no replacement happened, so no NOTICE may be emitted: " + warnings);
    }

    /**
     * When both rows exist the source's values replace the destination's, a
     * replacement NOTICE is emitted, and the destination keeps its own
     * {@code created_at} because its row is updated in place.
     */
    @Test
    void existingSourceReplacesDestinationAndKeepsItsCreatedAt() throws Exception {
        setTableLockSettings(PUBLIC_SCHEMA, OLD_NAME, 10, 20, 30);
        setCreatedAt(OLD_NAME, "2020-01-01 00:00:00+00");
        setTableLockSettings(PUBLIC_SCHEMA, NEW_NAME, 100, 200, 300);
        setCreatedAt(NEW_NAME, "2010-01-01 00:00:00+00");

        List<String> warnings = moveAndCollectWarnings(OLD_NAME, NEW_NAME);

        assertNull(getTableLockSettings(PUBLIC_SCHEMA, OLD_NAME));
        Record settings = getTableLockSettings(PUBLIC_SCHEMA, NEW_NAME);
        assertLockSettings(settings, 10, 20, 30);
        assertTrue(createdAtIs(NEW_NAME, "2010-01-01 00:00:00+00"),
                "a destination row updated in place keeps its own created_at");
        assertTrue(warnings.stream().anyMatch(message -> message.contains(REPLACEMENT_NOTICE)),
                () -> "replacing an existing destination row requires the NOTICE: " + warnings);
    }

    /**
     * Calls the move from a caller connection and returns the server NOTICEs
     * the driver attaches to the statement, so a test can assert that the
     * replacement warning is emitted only when it is true.
     */
    private List<String> moveAndCollectWarnings(String oldName, String newName) throws SQLException {
        try (Connection connection = openTestConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT ddl_utils.move_table_lock_settings(?, ?, ?)")) {
            statement.setString(1, PUBLIC_SCHEMA);
            statement.setString(2, oldName);
            statement.setString(3, newName);
            statement.execute();

            List<String> messages = new ArrayList<>();
            for (SQLWarning warning = statement.getWarnings(); warning != null;
                 warning = warning.getNextWarning()) {
                messages.add(warning.getMessage());
            }
            return messages;
        }
    }

    /**
     * Sets a settings row's {@code created_at} to a known value so the test can
     * tell preservation from a fresh default. Runs as the owner because callers
     * only have SELECT on the settings table.
     */
    private void setCreatedAt(String tableName, String timestamp) throws SQLException {
        try (Connection owner = openOwnerConnection();
             PreparedStatement statement = owner.prepareStatement(
                     """
                             UPDATE ddl_utils.table_lock_settings
                             SET created_at = ?::timestamptz
                             WHERE schema_name = ? AND table_name = ?
                             """)) {
            statement.setString(1, timestamp);
            statement.setString(2, PUBLIC_SCHEMA);
            statement.setString(3, tableName);
            statement.execute();
        }
    }

    /**
     * Returns whether the settings row's {@code created_at} equals the given
     * instant, compared by PostgreSQL so the session time zone cannot affect
     * the result.
     */
    private boolean createdAtIs(String tableName, String timestamp) {
        return dsl.fetchOne(
                """
                        SELECT created_at = ?::timestamptz
                        FROM ddl_utils.table_lock_settings
                        WHERE schema_name = ? AND table_name = ?
                        """,
                timestamp, PUBLIC_SCHEMA, tableName).get(0, Boolean.class);
    }
}

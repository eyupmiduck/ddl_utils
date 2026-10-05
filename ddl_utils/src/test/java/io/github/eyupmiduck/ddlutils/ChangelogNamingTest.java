package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogAssertions;
import io.github.eyupmiduck.changelogvalidator.testing.ChangelogTestSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Verifies the naming conventions enforced on the Liquibase changelog. SQL
 * files must start with a three-digit, zero-padded prefix (for example
 * {@code 001-create-schema}), followed by a hyphen or an underscore. ChangeSet
 * ids either follow the same {@code NNN-name} pattern (schema/table changesets)
 * or name the routine or trigger they load:
 * {@code function-<schema>.<name>}, {@code procedure-<schema>.<name>} and
 * {@code trigger-<schema>.<name>}. Overloads of one routine share a
 * changeset, so the id is the routine's schema and name, not its signature.
 */
class ChangelogNamingTest {

    /**
     * The changeSet id rule: the {@code NNN-name} pattern, or a stored-object
     * id of the form {@code function-<schema>.<name>} /
     * {@code procedure-<schema>.<name>} / {@code trigger-<schema>.<name>}.
     */
    private static final Pattern CHANGE_SET_ID = Pattern.compile(
            "\\d{3}[-_].+|(function|procedure|trigger)-[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Scans the changelog changes directory and asserts that no SQL file name
     * violates the {@code NNN-name.sql} pattern.
     */
    @Test
    void sqlFilesHaveThreeDigitPrefix() throws IOException {
        ChangelogAssertions.assertSqlFilesAreNumbered(ChangelogTestSupport.changesRoot());
    }

    /**
     * Traverses the changelog graph from the master file and asserts that no
     * changeSet id violates the naming convention.
     */
    @Test
    void changeSetsFollowTheNamingConvention() throws IOException {
        ChangelogAssertions.assertChangeSetsFollowNaming(
                ChangelogTestSupport.changelogRoot(), ChangelogTestSupport.master(), CHANGE_SET_ID);
    }
}

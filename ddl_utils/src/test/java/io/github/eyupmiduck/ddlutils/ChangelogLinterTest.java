package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.linter.Finding;
import io.github.eyupmiduck.changelogvalidator.linter.Linter;
import io.github.eyupmiduck.changelogvalidator.linter.config.LinterConfig;
import io.github.eyupmiduck.changelogvalidator.linter.model.ChangeSet;
import io.github.eyupmiduck.changelogvalidator.linter.model.ChangelogModel;
import io.github.eyupmiduck.changelogvalidator.linter.model.SqlSource;
import io.github.eyupmiduck.changelogvalidator.linter.rules.Rules;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the Liquibase changelog linter (bead ddl-w8y) over the module changelog,
 * mirroring the {@code liquibase-linter} {@code verify} gate in {@code pom.xml},
 * and checks that the gate would actually fail on a bad changeset.
 *
 * <p>The linter rules combine the SQL tokens with Liquibase changeset semantics
 * (for example {@code runInTransaction}), which SQLFluff and plpgsql_check
 * cannot see.
 */
class ChangelogLinterTest {

    /**
     * The changelog lints clean with the default configuration and the default
     * rule set for the build's PostgreSQL version.
     */
    @Test
    void changelogPassesTheLinter() throws IOException, URISyntaxException {
        URL changelogUrl = getClass().getClassLoader().getResource("db/changelog");
        assertNotNull(changelogUrl, "changelog directory must be on the test classpath");
        Path changelogRoot = Path.of(changelogUrl.toURI());
        Path master = changelogRoot.resolve("db.changelog-master.xml");

        List<ChangeSet> changeSets = ChangelogModel.changesets(changelogRoot, master);
        Linter linter = new Linter(Rules.all(17), LinterConfig.defaults());

        List<Finding> findings = linter.lint(changeSets);

        assertTrue(findings.isEmpty(), () -> "unexpected linter findings:\n" + findings.stream()
                .map(Finding::message)
                .collect(Collectors.joining("\n")));
        assertFalse(linter.fails(findings), "a changelog with no findings must pass");
    }

    /**
     * A transaction-forbidden statement in a transactional changeset is reported
     * and fails the run, so the verify gate can fail a deliberately bad
     * changeset.
     */
    @Test
    void transactionForbiddenStatementInATransactionalChangesetFails() throws IOException {
        SqlSource forward = new SqlSource(
                SqlSource.Kind.INLINE_SQL,
                null,
                "CREATE INDEX CONCURRENTLY example_name_idx ON ddl_utils.example (name);",
                true,
                ";",
                false,
                null);
        ChangeSet changeSet = new ChangeSet(
                "999-deliberate",
                "test",
                Path.of("deliberate-changelog.xml"),
                true,
                false,
                null,
                null,
                null,
                List.of(forward),
                false,
                List.of());

        Linter linter = new Linter(Rules.all(17), LinterConfig.defaults());
        List<Finding> findings = linter.lint(List.of(changeSet));

        assertEquals(
                List.of("changeset-run-in-transaction-required"),
                findings.stream().map(Finding::ruleId).toList());
        assertTrue(linter.fails(findings), "an error finding must fail the run");
    }
}

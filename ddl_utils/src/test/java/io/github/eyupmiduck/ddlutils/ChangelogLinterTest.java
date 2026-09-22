package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.linter.Finding;
import io.github.eyupmiduck.changelogvalidator.linter.Linter;
import io.github.eyupmiduck.changelogvalidator.linter.config.LinterConfig;
import io.github.eyupmiduck.changelogvalidator.linter.config.Whitelist;
import io.github.eyupmiduck.changelogvalidator.linter.model.ChangeSet;
import io.github.eyupmiduck.changelogvalidator.linter.model.ChangelogModel;
import io.github.eyupmiduck.changelogvalidator.linter.model.SqlSource;
import io.github.eyupmiduck.changelogvalidator.linter.rules.Rules;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
        Linter linter = new Linter(Rules.all(17), LinterConfig.defaults());
        List<Finding> findings = linter.lint(List.of(deliberatelyBadChangeSet()));

        assertEquals(
                List.of("changeset-run-in-transaction-required"),
                findings.stream().map(Finding::ruleId).toList());
        assertTrue(linter.fails(findings), "an error finding must fail the run");
    }

    /**
     * A finding accepted by a whitelist entry is suppressed, so an intentional
     * violation can be carried without failing the gate.
     */
    @Test
    void whitelistedFindingPasses() throws IOException {
        Linter linter = new Linter(Rules.all(17), LinterConfig.defaults());
        List<Finding> findings = linter.lint(List.of(deliberatelyBadChangeSet()));

        Whitelist.Report report = whitelist("""
                - rule: changeset-run-in-transaction-required
                  changeset: 999-deliberate
                  statement: CREATE INDEX CONCURRENTLY
                  reason: deliberate change in a test
                """).apply(findings);

        assertTrue(report.isEmpty(), () -> "unexpected report: " + report);
    }

    /**
     * A whitelist entry that matches no finding is stale and fails the run, so
     * the whitelist cannot rot.
     */
    @Test
    void staleWhitelistEntryIsReported() throws IOException {
        Linter linter = new Linter(Rules.all(17), LinterConfig.defaults());
        List<Finding> findings = linter.lint(List.of(deliberatelyBadChangeSet()));

        Whitelist.Report report = whitelist("""
                - rule: changeset-run-in-transaction-required
                  changeset: 999-gone
                  reason: the changeset was removed
                """).apply(findings);

        assertEquals(1, report.unmatched().size(), "the real finding is not accepted");
        assertEquals(1, report.stale().size(), "the entry matches nothing and is stale");
    }

    private static Whitelist whitelist(String yaml) throws IOException {
        return Whitelist.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private static ChangeSet deliberatelyBadChangeSet() {
        SqlSource forward = new SqlSource(
                SqlSource.Kind.INLINE_SQL,
                null,
                "CREATE INDEX CONCURRENTLY example_name_idx ON ddl_utils.example (name);",
                true,
                ";",
                false,
                null);
        return new ChangeSet(
                "999-deliberate",
                "test",
                Path.of("deliberate-changelog.xml"),
                true,
                false,
                "postgresql",
                null,
                null,
                List.of(forward),
                false,
                List.of());
    }
}

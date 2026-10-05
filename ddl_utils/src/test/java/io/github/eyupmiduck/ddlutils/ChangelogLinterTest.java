package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.linter.Finding;
import io.github.eyupmiduck.changelogvalidator.linter.Linter;
import io.github.eyupmiduck.changelogvalidator.linter.config.LinterConfig;
import io.github.eyupmiduck.changelogvalidator.linter.config.Whitelist;
import io.github.eyupmiduck.changelogvalidator.linter.model.ChangeSet;
import io.github.eyupmiduck.changelogvalidator.linter.model.ChangelogModel;
import io.github.eyupmiduck.changelogvalidator.linter.model.SqlSource;
import io.github.eyupmiduck.changelogvalidator.linter.rules.Rules;
import io.github.eyupmiduck.changelogvalidator.testing.ChangelogTestSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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

    private static Whitelist whitelist(String yaml) throws IOException {
        return Whitelist.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The PostgreSQL major version the build is configured for, parsed from the
     * same {@code postgres.image} system property the tests use, so this test
     * cannot silently diverge from the build's {@code postgres.version}.
     */
    private static int postgresMajorVersion() {
        String image = System.getProperty("postgres.image", "ddl-utils-postgres:17-alpine");
        String tag = image.substring(image.indexOf(':') + 1);
        return Integer.parseInt(tag.replaceAll("^(\\d+).*$", "$1"));
    }

    /**
     * The verify gate passes only when every finding is accepted and no
     * whitelist entry is stale.
     */
    private static boolean passesGate(Whitelist.Report report) {
        return report.unmatched().isEmpty() && report.stale().isEmpty();
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

    /**
     * The changelog lints clean with the default configuration and the default
     * rule set for the build's PostgreSQL version.
     */
    @Test
    void changelogPassesTheLinter() throws IOException {
        Path changelogRoot = ChangelogTestSupport.changelogRoot();
        Path master = ChangelogTestSupport.master();

        List<ChangeSet> changeSets = ChangelogModel.changesets(changelogRoot, master);
        Linter linter = new Linter(Rules.all(postgresMajorVersion()), LinterConfig.defaults());

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
        ChangeSet bad = deliberatelyBadChangeSet();

        // Pin the fixture's semantics so the test cannot pass for an unrelated
        // reason (for example the changeset being non-transactional).
        assertEquals("999-deliberate", bad.id());
        assertTrue(bad.runInTransaction(), "the fixture must be transactional");
        assertFalse(bad.runOnChange());
        assertEquals("postgresql", bad.dbms());
        assertEquals(SqlSource.Kind.INLINE_SQL, bad.sqlSources().getFirst().kind());

        Linter linter = new Linter(Rules.all(postgresMajorVersion()), LinterConfig.defaults());
        List<Finding> findings = linter.lint(List.of(bad));

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
        Linter linter = new Linter(Rules.all(postgresMajorVersion()), LinterConfig.defaults());
        List<Finding> findings = linter.lint(List.of(deliberatelyBadChangeSet()));

        Whitelist.Report report = whitelist("""
                - rule: changeset-run-in-transaction-required
                  changeset: 999-deliberate
                  statement: CREATE INDEX CONCURRENTLY
                  reason: deliberate change in a test
                """).apply(findings);

        assertEquals(List.of(), report.unmatched());
        assertEquals(List.of(), report.stale());
        assertTrue(passesGate(report), () -> "unexpected report: " + report);
    }

    /**
     * A whitelist entry that matches no finding is stale and fails the run, so
     * the whitelist cannot rot.
     */
    @Test
    void staleWhitelistEntryIsReported() throws IOException {
        Linter linter = new Linter(Rules.all(postgresMajorVersion()), LinterConfig.defaults());
        List<Finding> findings = linter.lint(List.of(deliberatelyBadChangeSet()));

        Whitelist.Report report = whitelist("""
                - rule: changeset-run-in-transaction-required
                  changeset: 999-gone
                  reason: the changeset was removed
                """).apply(findings);

        assertFalse(passesGate(report), "a stale entry must fail the gate");

        assertEquals(1, report.unmatched().size(), "the real finding is not accepted");
        Finding unmatched = report.unmatched().getFirst();
        assertEquals("changeset-run-in-transaction-required", unmatched.ruleId());
        assertEquals("999-deliberate", unmatched.changeSetId());

        assertEquals(1, report.stale().size(), "the entry matches nothing and is stale");
        Whitelist.AllowedFinding stale = report.stale().getFirst();
        assertEquals("changeset-run-in-transaction-required", stale.rule());
        assertEquals("999-gone", stale.changeset());
    }
}

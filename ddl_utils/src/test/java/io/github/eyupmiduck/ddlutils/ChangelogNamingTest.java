package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ChangelogNamingContract;

/**
 * Verifies the naming conventions enforced on the Liquibase changelog, inheriting
 * the shared {@link ChangelogNamingContract}: SQL files must start with a
 * three-digit prefix, and changeSet ids must follow the shared pattern. The
 * routine/trigger directories are exempt from the file-name rule, as documented
 * in AGENTS.md.
 */
class ChangelogNamingTest extends ChangelogNamingContract {
}

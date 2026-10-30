package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.ExtensionAssertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

/**
 * Verifies that the extension compiled into the custom image is available to
 * install and installed in every test database: {@code plpgsql_check}, which the
 * routine analyser uses. Delegates to the shared {@link ExtensionAssertions}.
 */
class PostgresExtensionsTest extends PostgresTestBase {

    private static final List<String> EXTENSIONS = List.of("plpgsql_check");

    /**
     * The extension is both available and installed.
     *
     * @throws Exception if the catalog cannot be read
     */
    @Test
    void extensionsAreAvailableAndInstalled() throws Exception {
        try (Connection connection = openTestConnection()) {
            ExtensionAssertions.assertExtensionsAvailable(connection, EXTENSIONS);
            ExtensionAssertions.assertExtensionsInstalled(connection, EXTENSIONS);
        }
    }
}

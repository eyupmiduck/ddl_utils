package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.PlpgsqlCheckContract;
import org.junit.jupiter.api.Test;

import java.util.Collection;

/**
 * Runs the {@code plpgsql_check} static analyser over every routine in the
 * {@code ddl_utils} and {@code ddl_utils_lib} schemas, inheriting the shared
 * {@link PlpgsqlCheckContract}.
 *
 * <p>The extension is compiled into the custom image and installed into the
 * template database, so each cloned test database (and the dev database, via
 * {@code scripts/postgres/roles.sql}) has it.
 */
class PlpgsqlCheckTest extends PlpgsqlCheckContract {

    /**
     * The ddl_utils container and changelog configuration.
     *
     * @return the configuration
     */
    @Override
    protected Configuration configuration() {
        return PostgresTestBase.CONFIGURATION;
    }

    /**
     * The schemas that hold the project's routines, taken from the shared
     * constants so a schema rename cannot silently enumerate nothing.
     *
     * @return the routine schemas
     */
    @Override
    protected Collection<String> routineSchemas() {
        return PostgresTestBase.ROUTINE_SCHEMAS;
    }

    /**
     * Every configured routine schema exists and holds at least one routine, so
     * the inherited contract cannot pass by analysing an empty set.
     */
    @Test
    void routineSchemasArePopulated() {
        PostgresTestBase.assertRoutineSchemasArePopulated(dsl, routineSchemas());
    }
}

package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that Liquibase keeps its tracking tables out of the application
 * schemas: they live in the dedicated {@code liquibase} schema, prefixed with
 * {@code ddl_utils_}, rather than as the generic {@code databasechangelog} /
 * {@code databasechangeloglock} tables.
 */
class LiquibaseTrackingTablesTest extends PostgresTestBase {

    /**
     * Asserts that the tracking tables exist in the {@code liquibase} schema
     * with the {@code ddl_utils_} prefix, and that the generic names are absent
     * from the {@code public} and {@code ddl_utils} schemas.
     */
    @Test
    void trackingTablesLiveInTheLiquibaseSchema() {
        assertTrue(anyRelationExists(LIQUIBASE_SCHEMA, DATABASE_CHANGELOG_TABLE),
                "expected " + LIQUIBASE_SCHEMA + "." + DATABASE_CHANGELOG_TABLE);
        assertTrue(anyRelationExists(LIQUIBASE_SCHEMA, DATABASE_CHANGELOG_LOCK_TABLE),
                "expected " + LIQUIBASE_SCHEMA + "." + DATABASE_CHANGELOG_LOCK_TABLE);

        assertFalse(anyRelationExists("public", "databasechangelog"),
                "the generic public.databasechangelog should not exist");
        assertFalse(anyRelationExists("public", "databasechangeloglock"),
                "the generic public.databasechangeloglock should not exist");
        assertFalse(anyRelationExists("ddl_utils", "databasechangelog"),
                "the generic ddl_utils.databasechangelog should not exist");
        assertFalse(anyRelationExists("ddl_utils", "databasechangeloglock"),
                "the generic ddl_utils.databasechangeloglock should not exist");
    }

    /**
     * Returns whether any relation (table, view, sequence or index) exists,
     * reading the catalog directly so the lookup works for schemas the test
     * role has no privileges on. Unlike the inherited table-only
     * {@code relationExists}, this matches a relation of any kind, so a
     * misplaced sequence cannot masquerade as a satisfied tracking table.
     *
     * @param schema   the schema name
     * @param relation the relation name
     * @return {@code true} when the relation exists
     */
    private boolean anyRelationExists(String schema, String relation) {
        return Boolean.TRUE.equals(dsl.fetchValue(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM pg_class c
                            JOIN pg_namespace n ON n.oid = c.relnamespace
                            WHERE n.nspname = ? AND c.relname = ?
                        )
                        """,
                schema, relation));
    }
}

package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code ddl_utils_lib.resolve_table_relation}: the schema and table
 * names are cast to {@code ddl_utils.identifier}, so an over-long name is
 * rejected before {@code to_regclass} can truncate it and resolve a different
 * relation.
 */
class ResolveTableRelationTest extends PostgresTestBase {

    /**
     * A schema or table name longer than 63 bytes is rejected by the identifier
     * domain instead of being truncated.
     */
    @Test
    void rejectsOverLongSchemaAndTableNames() {
        assertDomainViolation(() -> Routines.resolveTableRelation(
                dsl.configuration(), "s".repeat(64), "t", "test", "table"));
        assertDomainViolation(() -> Routines.resolveTableRelation(
                dsl.configuration(), PUBLIC_SCHEMA, "t".repeat(64), "test", "table"));
    }

    /**
     * A 63-byte missing table passes the identifier domain and still fails as
     * undefined_table, so the domain guard does not hide the normal resolver
     * error.
     */
    @Test
    void acceptsNameAtTheLimitAndReportsMissingTable() {
        assertSqlState("42P01", () -> Routines.resolveTableRelation(
                dsl.configuration(), PUBLIC_SCHEMA, "t".repeat(63), "test", "table"));
    }
}

package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@code ddl_utils_lib.foreign_key_definition_matches}: it
 * compares the referenced relation, the column mapping and PostgreSQL's
 * default actions/match/deferrability.
 */
class ForeignKeyDefinitionMatchesTest extends PostgresTestBase {

    private static final String TARGET = "fk_matches_target";
    private static final String REFERENCED = "fk_matches_referenced";
    private static final String OTHER = "fk_matches_other";

    @BeforeEach
    void createTables() {
        createTestTable(REFERENCED, "id int PRIMARY KEY");
        createTestTable(OTHER, "id int PRIMARY KEY");
        createTestTable(TARGET, "parent_id int, other_id int");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TARGET
                + " ADD CONSTRAINT fk_cascade FOREIGN KEY (other_id) REFERENCES "
                + PUBLIC_SCHEMA + "." + REFERENCED + " (id) ON DELETE CASCADE");
    }

    @AfterEach
    void dropTables() {
        dropTestTable(TARGET);
        dropTestTable(REFERENCED);
        dropTestTable(OTHER);
    }

    /**
     * The matching referenced table and column mapping returns true.
     */
    @Test
    void matchesTheStoredDefinition() {
        assertTrue(matches("fk_parent", REFERENCED, "parent_id", "id"));
    }

    /**
     * A different source column, referenced table or referential action is a
     * mismatch.
     */
    @Test
    void rejectsDifferentDefinitions() {
        assertFalse(matches("fk_parent", REFERENCED, "other_id", "id"));
        assertFalse(matches("fk_parent", OTHER, "parent_id", "id"));
        assertFalse(matches("fk_cascade", REFERENCED, "other_id", "id"),
                "a non-default ON DELETE action must not match");
    }

    private boolean matches(String constraint, String referencedTable,
                            String sourceColumn, String referencedColumn) {
        return evaluate("ddl_utils_lib.foreign_key_definition_matches('" + PUBLIC_SCHEMA + "', '"
                + TARGET + "', '" + constraint + "', '" + PUBLIC_SCHEMA + "', '" + referencedTable
                + "', ARRAY['" + sourceColumn + "']::ddl_utils.non_empty_non_null_text_array, "
                + "ARRAY['" + referencedColumn + "']::ddl_utils.non_empty_non_null_text_array)",
                Boolean.class);
    }
}

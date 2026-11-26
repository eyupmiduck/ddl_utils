package io.github.eyupmiduck.ddlutils;

import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct tests for {@code ddl_utils_lib.constraint_state}: it returns the OID
 * and validation flag for a named constraint of the requested kind, and no row
 * for a missing or different-kind constraint.
 */
class ConstraintStateTest extends PostgresTestBase {

    private static final String TABLE = "constraint_state_target";

    @BeforeEach
    void createTable() {
        createTestTable(TABLE, "id int, note text");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TABLE
                + " ADD CONSTRAINT c_valid CHECK (id > 0)");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TABLE
                + " ADD CONSTRAINT c_not_valid CHECK (note <> 'x') NOT VALID");
    }

    @AfterEach
    void dropTable() {
        dropTestTable(TABLE);
    }

    /**
     * A validated and an unvalidated CHECK report their respective flags.
     */
    @Test
    void reportsValidationState() {
        Record valid = state("c_valid", "c");
        assertNotNull(valid);
        assertNotNull(valid.get("constraint_oid"));
        assertEquals(true, valid.get("convalidated", Boolean.class));

        Record notValid = state("c_not_valid", "c");
        assertNotNull(notValid);
        assertEquals(false, notValid.get("convalidated", Boolean.class));
    }

    /**
     * A different constraint kind and a missing name return no row.
     */
    @Test
    void returnsNoRowForDifferentKindOrMissingConstraint() {
        assertNull(state("c_valid", "f"));
        assertNull(state("missing", "c"));
    }

    private Record state(String constraint, String contype) {
        return dsl.fetchOne("SELECT * FROM ddl_utils_lib.constraint_state('" + PUBLIC_SCHEMA + "."
                + TABLE + "'::regclass, '" + constraint + "', '" + contype + "')");
    }
}

package io.github.eyupmiduck.ddlutils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for {@code ddl_utils_lib.constraint_of_other_kind_exists}: a
 * same-named constraint of a different kind is reported, while the requested
 * kind and a missing name are not.
 */
class ConstraintOfOtherKindExistsTest extends PostgresTestBase {

    private static final String TABLE = "constraint_kind_target";

    @BeforeEach
    void createTable() {
        createTestTable(TABLE, "id int, note text");
        dsl.execute("ALTER TABLE " + PUBLIC_SCHEMA + "." + TABLE
                + " ADD CONSTRAINT c_note CHECK (note IS NOT NULL)");
    }

    @AfterEach
    void dropTable() {
        dropTestTable(TABLE);
    }

    /**
     * A CHECK is another kind when the caller asks for a foreign key, but not
     * when it asks for a CHECK.
     */
    @Test
    void reportsDifferentKindOnly() {
        assertEquals(true, otherKind("c_note", "f"));
        assertEquals(false, otherKind("c_note", "c"));
    }

    /**
     * A missing name is not an other-kind constraint.
     */
    @Test
    void reportsMissingConstraintAsAbsent() {
        assertEquals(false, otherKind("missing", "c"));
    }

    private boolean otherKind(String constraint, String contype) {
        return evaluate("ddl_utils_lib.constraint_of_other_kind_exists('" + PUBLIC_SCHEMA + "."
                + TABLE + "'::regclass, '" + constraint + "', '" + contype + "')", Boolean.class);
    }
}

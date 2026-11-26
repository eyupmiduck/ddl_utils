package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.changelogvalidator.testing.PrivilegeContract;
import io.github.eyupmiduck.changelogvalidator.testing.ProjectProfile;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the caller grants and default-privilege hardening, inheriting the
 * shared {@link PrivilegeContract}: no routine or application type is usable by
 * {@code PUBLIC}, the caller can execute every callable routine and use every
 * domain/type, a routine or type created later does not regain {@code PUBLIC}
 * access, and the grant rollback mirrors the forward grants.
 *
 * <p>The ddl_utils-specific trigger function is checked separately because the
 * shared "every callable routine" assertion excludes trigger functions.
 */
class PrivilegeTest extends PrivilegeContract {

    /**
     * Every domain the caller may use, plus the composite type returned by
     * {@code get_lock_settings}.
     *
     * @return the application types
     */
    @Override
    protected Collection<TypeRef> applicationTypes() {
        return List.of(
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "non_negative_integer"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "non_null_text"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "identifier"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "sql_prefix"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "non_null_boolean"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "non_empty_text_array"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "non_empty_non_null_text_array"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "non_empty_non_null_boolean_array"),
                new TypeRef(PostgresTestBase.DDL_UTILS_SCHEMA, "lock_settings"));
    }

    /**
     * The shared trigger function is invoked by the trigger machinery rather than
     * by callers, so the application role must not be able to call it directly.
     * The shared "every callable routine" assertion excludes trigger functions, so
     * this class enforces the exclusion. The function is asserted to exist first,
     * so a renamed or removed {@code set_updated_at} cannot make the zero-count
     * check pass vacuously.
     */
    @Test
    void triggerFunctionIsNotExecutableByCaller() {
        Integer triggerFunctions = dsl.fetchOne("""
                SELECT count(*)::int
                FROM pg_proc AS p
                JOIN pg_namespace AS n ON n.oid = p.pronamespace
                WHERE n.nspname = ?
                  AND p.proname = 'set_updated_at'
                  AND p.prorettype = 'trigger'::pg_catalog.regtype
                """, PostgresTestBase.DDL_UTILS_SCHEMA).get(0, Integer.class);
        assertEquals(1, triggerFunctions,
                "the shared set_updated_at trigger function must exist exactly once");

        Integer triggerExecutable = dsl.fetchOne("""
                        SELECT count(*)::int
                        FROM pg_proc AS p
                        JOIN pg_namespace AS n ON n.oid = p.pronamespace
                        WHERE n.nspname = ?
                          AND p.proname = 'set_updated_at'
                          AND p.prorettype = 'trigger'::pg_catalog.regtype
                          AND pg_catalog.has_function_privilege(?, p.oid, 'EXECUTE')
                        """, PostgresTestBase.DDL_UTILS_SCHEMA, PostgresTestBase.DDL_UTILS_CALLER)
                .get(0, Integer.class);
        assertEquals(0, triggerExecutable,
                "the trigger function must not be executable by " + PostgresTestBase.DDL_UTILS_CALLER);
    }

    /**
     * The ddl_utils profile, so this contract uses the project's roles, image,
     * changelog and schemas rather than the library defaults.
     *
     * @return the ddl_utils profile
     */
    @Override
    protected ProjectProfile projectProfile() {
        return PostgresTestBase.PROFILE;
    }
}

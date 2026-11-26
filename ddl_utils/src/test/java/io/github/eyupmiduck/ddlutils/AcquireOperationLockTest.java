package io.github.eyupmiduck.ddlutils;

import io.github.eyupmiduck.ddlutils.jooq.ddl_utils_lib.Routines;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@code ddl_utils_lib.acquire_operation_lock}: it takes the
 * transaction-scoped advisory lock for the supplied keys and releases it when
 * the transaction ends.
 */
class AcquireOperationLockTest extends PostgresTestBase {

    /**
     * While the caller's transaction is open, another session cannot take the
     * same advisory lock; after the transaction commits, it can.
     */
    @Test
    void holdsTheAdvisoryLockUntilTheTransactionEnds() throws Exception {
        String context = "acquire_operation_lock_test";
        String key = "a";
        long lockKey = ((Number) dsl.fetchValue(
                "SELECT ddl_utils_lib.operation_lock_key(?, ARRAY[?]::ddl_utils.non_empty_non_null_text_array)",
                context, key)).longValue();

        dsl.transaction(tx -> {
            Routines.acquireOperationLock(tx, context, new String[]{key});
            try (Connection other = openTestConnection()) {
                Boolean acquired = DSL.using(other)
                        .fetchOne("SELECT pg_catalog.pg_try_advisory_xact_lock(?) AS acquired", lockKey)
                        .get("acquired", Boolean.class);
                assertFalse(Boolean.TRUE.equals(acquired),
                        "the lock must be held until the transaction ends");
            }
        });

        try (Connection other = openTestConnection()) {
            Boolean acquired = DSL.using(other)
                    .fetchOne("SELECT pg_catalog.pg_try_advisory_xact_lock(?) AS acquired", lockKey)
                    .get("acquired", Boolean.class);
            assertTrue(Boolean.TRUE.equals(acquired),
                    "the lock must be released when the transaction ends");
        }
    }
}

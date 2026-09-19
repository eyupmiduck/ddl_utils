# Stored procedures

Multi-`ALTER TABLE` operations are implemented as procedures, not functions,
because a function cannot `COMMIT` mid-call: after its first `ALTER TABLE` the
table is held under a lock until the caller's transaction ends. A procedure can
`COMMIT` between steps and release each lock before the next step runs, which is
what makes a long verification scan tolerable.

One procedure per `.sql` file under `procedures/<schema>/`, named `snake_case`
without an `NNN-` prefix. `changes/procedures.xml` holds one `NNN-`prefixed
`runOnChange` changeset per procedure (a `createProcedure` plus its rollback);
`changes/procedures-rollback/<schema>/` holds the rollback bodies.

## The orchestration rule

A procedure **orchestrates**; it does not implement DDL or locking.

- Every step calls a purpose-named single-call **function** — preferably the
  matching `ddl_utils_lib` helper (`add_check_constraint`,
  `validate_constraint`, `drop_constraint`, `add_foreign_key`, `set_not_null`,
  …). If a needed single-call operation has no helper, add the helper first
  rather than inlining a fragment.
- A procedure **never** calls `ddl_utils_lib.alter_table` directly, and never
  contains a `lock_timeout`, retry, sleep or spin loop. All of that lives in the
  function layer (`ddl_utils_lib.alter_table` and the helpers built on it), so a
  procedure reads as a sequence of named operations.
- The only things a procedure owns are the `COMMIT` boundaries and the
  idempotency checks that decide whether each step still needs to run.

## Pattern (verified against PostgreSQL 17)

The template every procedure follows:

```sql
DECLARE
    l_lock integer; l_sleep integer; l_dur integer;
BEGIN
    -- Read the settings once for the whole call.
    SELECT ddl_lock_timeout, sleep_time, statement_duration
      INTO l_lock, l_sleep, l_dur
      FROM ddl_utils.get_lock_settings(i_schema_name, i_table_name);

    -- Step: skip when the catalog shows the work is already done.
    IF <step not yet done> THEN
        PERFORM ddl_utils_lib.<helper>(..., l_lock, l_sleep, l_dur);
        COMMIT;                      -- release the lock before the next step
    END IF;

    -- ... further steps, each its own IF/PERFORM/COMMIT ...
END;
```

Findings that the pattern rests on:

- **Calling a function per step works.** A procedure can `PERFORM` a
  single-call function, `COMMIT`, then `PERFORM` the next; the function's
  `SET LOCAL lock_timeout` and its exception/retry loop run correctly inside
  that step's transaction, and `lock_timeout` is restored before the next
  `COMMIT`.
- **Settings are read once.** `ddl_utils.get_lock_settings` is `STABLE`; the
  values are captured at the top of the call and reused across steps. They need
  not be re-read after a `COMMIT`.
- **`55P03` propagates by itself.** When a step cannot take its lock within the
  budget, the function raises `55P03`; it travels out of the procedure unchanged
  and leaves the transaction in a state the client can simply retry. The
  procedure needs no `EXCEPTION` block for it.
- **Partial failure is recoverable from the catalog.** Each step's guard tests
  the catalog (for example `pg_constraint.conname` / `convalidated`,
  `pg_attribute.attnotnull`), so a call that starts after a previous call
  committed some but not all steps resumes at the first unfinished step and
  completes with no human intervention.
- **A genuine data failure is left recoverable.** If `VALIDATE CONSTRAINT`
  fails with `23514`, the temporary constraint stays in place as `NOT VALID`;
  the caller fixes the data and re-runs, and the re-run validates, sets
  `NOT NULL`, and drops the temporary constraint.

## Caller contract

Because a procedure commits, it must run in **autocommit**. Calling it inside a
client-side transaction block fails with `invalid transaction termination`.
From JDBC/jOOQ that means autocommit on (the tests do this).

## Procedures

A procedure cannot share a name and argument types with a function, so the
multi-step procedures take an `ensure_` prefix while the single-call function of
the same name stays as-is (for example `ddl_utils.ensure_not_null` procedure vs
`ddl_utils.set_not_null` function). The `ensure_` name signals "make it so,
re-runnably".

### `ddl_utils.ensure_not_null(i_schema_name, i_table_name, i_column_name)`

```sql
i_schema_name ddl_utils.non_null_text
i_table_name  ddl_utils.non_null_text
i_column_name ddl_utils.non_null_text
```

Make a column `NOT NULL` without holding `ACCESS EXCLUSIVE` across the
verification scan. Steps, each committed before the next:

1. `ddl_utils_lib.add_check_constraint(<tmp>, '<col> IS NOT NULL')` — instant,
   `NOT VALID`, brief `ACCESS EXCLUSIVE`.
2. `ddl_utils_lib.validate_constraint(<tmp>)` — scans under `SHARE UPDATE
   EXCLUSIVE`.
3. `ddl_utils_lib.set_not_null(<col>)` — skips its scan because the valid
   `CHECK` proves the column non-null.
4. `ddl_utils_lib.drop_constraint(<tmp>)` — cleanup.

`<tmp>` is deterministic from the table and column, so a re-run finds the same
constraint. A call interrupted after any step is completed by re-running it.

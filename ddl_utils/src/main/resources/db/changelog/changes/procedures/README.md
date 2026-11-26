# Stored procedures

Multi-`ALTER TABLE` operations are implemented as procedures, not functions,
because a function cannot `COMMIT` mid-call: after its first `ALTER TABLE` the
table is held under a lock until the caller's transaction ends. A procedure can
`COMMIT` between steps and release each lock before the next step runs, which is
what makes a long verification scan tolerable.

One procedure per `.sql` file under `procedures/<schema>/`, named `snake_case`
without an `NNN-` prefix. `changes/procedures.xml` holds one
`procedure-<schema>.<name>` changeset per procedure (a `createProcedure` plus
its rollback); `changes/procedures-rollback/<schema>/` holds the rollback
bodies.

The changelog is deployed. Schema changesets are frozen, but procedure files are
edited in place: `runOnChange` re-applies the changed body on upgrade. Keep the
changeset id/path/attributes fixed and do not add versioned files. A signature
change adds `DROP PROCEDURE IF EXISTS <old signature>;` before the new
`CREATE OR REPLACE`, updates the paired rollback, and adds or updates the grant
in a new grant changeset. `RoutineSignatureTest` fails if a routine name has
more than one signature unless an intentional overload is allow-listed.

## The orchestration rule

A procedure **orchestrates**; it does not implement DDL or locking.

- Every step calls a purpose-named single-call **function** — preferably the
  matching `ddl_utils_lib` helper (`add_check_constraint`,
  `validate_constraint`, `drop_constraint`, `add_foreign_key`, `set_not_null`,
  …). If a needed single-call operation has no helper, add the helper first
  rather than inlining a fragment.
- A procedure **never** calls `ddl_utils_lib.alter_table` or
  `ddl_utils_lib.execute_with_lock_retry` directly, and never contains a
  `lock_timeout`, retry, sleep or spin loop. All of that lives in the function
  layer (`execute_with_lock_retry`, `alter_table`, `lock_foreign_key_tables`
  and the helpers built on them), so a procedure reads as a sequence of named
  operations.
- The only things a procedure owns are the `COMMIT` boundaries and the
  idempotency checks that decide whether each step still needs to run.
- **A single-step operation is a function, not a procedure.** A catalog check
  followed by one `ALTER TABLE` does not need a procedure, even when it is
  idempotent; use a function so callers do not pay the autocommit/`CALL` cost.
  Only multiple `ALTER TABLE` calls with `COMMIT`s justify a procedure.

## Pattern (verified against PostgreSQL 17)

The template every procedure follows:

```sql
DECLARE
    l_settings ddl_utils.lock_settings;
BEGIN
    -- Read the settings once for the whole call. The helper projects the
    -- columns by name, so a reorder cannot silently swap them.
    l_settings := ddl_utils_lib.table_lock_settings(
            i_schema_name => i_schema_name,
            i_table_name => i_table_name);

    -- Step: skip when the catalog shows the work is already done.
    IF <step not yet done> THEN
        PERFORM ddl_utils_lib.<helper>(...,
                i_ddl_lock_timeout => l_settings.ddl_lock_timeout,
                i_sleep_time => l_settings.sleep_time,
                i_statement_duration => l_settings.statement_duration);
        COMMIT; -- release the lock before the next step
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
- **Settings are read once.** `ddl_utils_lib.table_lock_settings` is `STABLE`;
  the values are captured at the top of the call and reused across steps. They
  need not be re-read after a `COMMIT`. `ensure_foreign_key` reads one settings
  set per table, because the add and validate steps lock both tables.
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

## External DDL between steps

The transaction-scoped advisory lock is released at every `COMMIT`, so it
serializes concurrent calls to the same procedure but does **not** block
arbitrary external DDL. Each step therefore re-acquires the lock and re-reads
the catalog before acting:

- The relation is re-resolved by name and its `relkind` re-checked. A relation
  dropped or renamed in the gap raises `42P01` (`undefined_table`), and one that
  is no longer an ordinary/partitioned table raises `42809`; the call fails
  closed instead of acting on a stale OID.
- A relation dropped and recreated under the same name is treated as the target
  relation: the procedures address tables by name, not OID. Do not replace a
  table while a committed procedure is in flight on it.
- `ensure_not_null` raises `55000` if the column was made nullable between the
  `SET NOT NULL` step and cleanup, and leaves the temporary proof in place. A
  re-run sets `NOT NULL` again and removes the proof.
- `ensure_foreign_key` compares the stored definition with the caller's column
  names. A column renamed in the gap makes the stored definition use the new
  name, so step 2 raises `42710` and leaves the key `NOT VALID`; re-run with the
  new names.
- `ensure_check_constraint` re-reads the constraint by name after the `COMMIT`:
  a vanished constraint raises `42704`, and a same-named constraint of another
  kind raises `42710`. A same-named `CHECK` is treated as the target and its
  expression is not compared (see below).

The guards close the window **between** steps: each step's catalog re-read sees
external DDL that committed before it. Within a step, the re-read and the
helper's `ALTER TABLE` are separate statements, so unrelated DDL can still
commit in the gap between them; the helper's table lock serializes the
statements themselves, not the earlier read. Serialize unrelated DDL on a table
while a committed procedure is in flight on it.

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
verification scan. `CALL` it outside an explicit transaction block (autocommit): the `COMMIT`s below fail with `2D000`
otherwise. Steps, each
committed before the next:

Each step re-acquires the transaction-scoped advisory lock, re-resolves the
relation by name (and re-checks `relkind`), and re-reads the column/constraint
state, because a `COMMIT` releases the lock, a concurrent call may have
completed one or more steps, and external DDL is not blocked. The call is
name-addressed, so a relation dropped or renamed in the gap fails closed with
`42P01`/`42809` instead of continuing on a stale OID.

1. `ddl_utils_lib.add_check_constraint(<tmp>, '<col> IS NOT NULL')` — instant,
   `NOT VALID`, brief `ACCESS EXCLUSIVE`. Skipped when the column is already
   `NOT NULL` or `<tmp>` already exists.
2. `ddl_utils_lib.validate_constraint(<tmp>)` — scans under `SHARE UPDATE
   EXCLUSIVE`. Skipped when the column is already `NOT NULL` or `<tmp>` is
   already valid.
3. `ddl_utils_lib.set_not_null(<col>)` — skips its scan because the valid
   `CHECK` proves the column non-null. Skipped when the column is already
   `NOT NULL`.
4. `ddl_utils_lib.drop_constraint(<tmp>)` — cleanup; runs when the temporary
   proof still exists **and the column is still `NOT NULL`**, so a leftover
   constraint is removed and a concurrent call's cleanup is not repeated. If
   external DDL made the column nullable in the gap, the call raises `55000`
   and leaves the proof in place; a re-run completes the column and removes it.

`<tmp>` is deterministic from the table and column, so a re-run finds the same
constraint. A call interrupted after any step is completed by re-running it. A
call on an already-`NOT NULL` column performs no `ALTER TABLE` at all, so a
re-applied migration does not re-take an `ACCESS EXCLUSIVE` lock or re-scan the
table.

A partitioned parent (`relkind` `p`) is accepted, but `ADD CONSTRAINT`,
`VALIDATE CONSTRAINT`, `SET NOT NULL` and `DROP CONSTRAINT` recurse to every
partition and each partition is locked `ACCESS EXCLUSIVE` for its statement.
The lock/retry settings bound only the parent statement, so the
no-long-`ACCESS EXCLUSIVE` guarantee does not cover the partitions; account for
them in the maintenance window.

### `ddl_utils.ensure_check_constraint(i_schema_name, i_table_name, i_constraint_name, i_check_expression)`

```sql
i_schema_name      ddl_utils.non_null_text
i_table_name       ddl_utils.non_null_text
i_constraint_name  ddl_utils.non_null_text
i_check_expression ddl_utils.non_null_text
```

Add a `CHECK` constraint and validate it in two committed steps, so the `ACCESS
EXCLUSIVE` lock of `ADD CONSTRAINT` is released before the scan. The constraint
name must be at most 63 bytes (`22023`); PostgreSQL would otherwise truncate it
and the validation/recovery guard would not find it. The source relation must be
an ordinary or partitioned table (`relkind` `r`/`p`); a missing relation raises
`42P01` and a non-table `42809` before any DDL.

1. `ddl_utils_lib.add_check_constraint(<name>, <expr>)` — `NOT VALID`, instant.
2. `ddl_utils_lib.validate_constraint(<name>)` — scans under `SHARE UPDATE
   EXCLUSIVE`.

A re-run skips the add when the constraint exists and the validate when it is
already valid. A violation by existing rows fails validation with `23514` and
leaves the constraint `NOT VALID`; fix the data and re-run.

Each step re-takes the transaction-scoped advisory lock after the `COMMIT` and
re-resolves the relation and constraint, because external DDL is not blocked by
the advisory lock: a relation dropped in the gap raises `42P01`, a vanished
constraint `42704`, and a same-named constraint of another kind `42710`. The
same-kind conflict guard also runs under the first lock.

The constraint **name is the identity**: a same-named `CHECK` is treated as the
target and its expression is not re-checked. A `CHECK` expression cannot be
compared exactly (`pg_get_constraintdef` returns a normalized, version-dependent
form), so use a distinct name per expression.

###

`ddl_utils.ensure_foreign_key(i_schema_name, i_table_name, i_constraint_name, i_column_names, i_referenced_schema_name, i_referenced_table_name, i_referenced_column_names)`

```sql
i_schema_name             ddl_utils.non_null_text
i_table_name              ddl_utils.non_null_text
i_constraint_name         ddl_utils.non_null_text
i_column_names            ddl_utils.non_empty_non_null_text_array
i_referenced_schema_name  ddl_utils.non_null_text
i_referenced_table_name   ddl_utils.non_null_text
i_referenced_column_names ddl_utils.non_empty_non_null_text_array
```

Same two committed steps as `ensure_check_constraint`, for a foreign key:
`ddl_utils_lib.add_foreign_key` (NOT VALID, `SHARE ROW EXCLUSIVE` on both
tables) then `ddl_utils_lib.validate_constraint`. Idempotent and recoverable the
same way. The procedure reads both tables' effective settings and, before each
step, calls `lock_foreign_key_tables` so both tables are locked in a
deterministic order with their own budgets: the add step uses `SHARE ROW
EXCLUSIVE` on both, and the validate step uses `SHARE UPDATE EXCLUSIVE` on the
source and `SHARE ROW EXCLUSIVE` on the referenced table. The constraint name
must be at most 63 bytes (`22023`), as for `ensure_check_constraint`; the source
and referenced relations must be ordinary or partitioned tables (`relkind`
`r`/`p`), otherwise a missing relation raises `42P01` and a non-table `42809`.

Even a `NOT VALID` foreign key requires the referenced columns to be covered by
a unique index or constraint (a bare unique index is accepted). The procedure
checks that before any DDL and raises `42830` with its own message instead of
the generic `ALTER TABLE` error.

Unlike a `CHECK`, a foreign key can be compared exactly, so a same-named key
that references different columns or a different table is not silently accepted:
the procedure compares `confrelid` and the `conkey`/`confkey` mapping as a
multiset (PostgreSQL may reorder the keys) and raises `42710` when the definition
differs. The mismatch message includes the stored definition. A
transaction-scoped advisory lock is keyed on the sorted pair of relations, and
`lock_foreign_key_tables` takes the actual table locks in OID order, so
reciprocal `ensure_foreign_key(A -> B)` and `(B -> A)` calls cannot deadlock.

After the `COMMIT`, step 2 re-takes the lock and re-resolves both relations and
the constraint: a relation dropped in the gap raises `42P01`, a vanished
constraint `42704`, and a constraint whose definition changed `42710`, instead
of silently skipping validation or validating the wrong key.

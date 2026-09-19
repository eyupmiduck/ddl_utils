# Stored functions

One function per `.sql` file, grouped by the schema that owns it:

- `ddl_utils/` — lock-settings tables/accessors and the lock-aware DDL wrappers.
- `ddl_utils_lib/` — generic DDL helpers that take the lock settings explicitly.
  `alter_table` is the internal runner they build on (the only dynamic-SQL
  boundary); prefer the structured helpers, which assemble the fragment from
  validated identifiers.

`changes/functions.xml` loads them one `createProcedure` per `runOnChange`
changeset; the matching drop lives in `changes/functions-rollback/`. Every
routine is `SECURITY INVOKER` except the setters/clearers, which are
`SECURITY DEFINER` (callers only have `SELECT` on the settings tables).

The three lock settings are `ddl_lock_timeout` (ms before a lock attempt gives
up), `sleep_time` (ms between retries) and `statement_duration` (ms budget for
acquiring the lock).

## `ddl_utils`

### `ddl_utils.get_database_lock_settings()`

```sql
RETURNS TABLE
(ddl_lock_timeout integer, sleep_time integer, statement_duration integer)
```

`STABLE`, `SECURITY INVOKER`. Returns the singleton database defaults (`database_lock_settings.id = 1`); raises `P0002`
when the row is missing.

### `ddl_utils.set_database_lock_settings(i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY DEFINER`. Overwrites the singleton row; raises `P0002` when it is
missing. Last writer wins.

### `ddl_utils.get_schema_lock_settings(i_schema_name)`

```sql
i_schema_name ddl_utils.non_null_text
RETURNS TABLE
(ddl_lock_timeout integer, sleep_time integer, statement_duration integer)
```

`STABLE`, `SECURITY INVOKER`. Returns the schema's row, or no row when there is
none.

### `ddl_utils.set_schema_lock_settings(i_schema_name, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name        ddl_utils.non_null_text
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY DEFINER`. Upserts the schema's settings. The schema name is stored as
given (not resolved against `pg_namespace`), so settings can be provisioned
before the schema exists; a typo simply never matches.

### `ddl_utils.clear_schema_lock_settings(i_schema_name)`

```sql
i_schema_name ddl_utils.non_null_text
RETURNS void
```

`SECURITY DEFINER`. Deletes the schema's row; a no-op when there is none.

### `ddl_utils.get_table_lock_settings(i_schema_name, i_table_name)`

```sql
i_schema_name ddl_utils.non_null_text
i_table_name  ddl_utils.non_null_text
RETURNS TABLE
(ddl_lock_timeout integer, sleep_time integer, statement_duration integer)
```

`STABLE`, `SECURITY INVOKER`. Returns the table's row, or no row when there is
none.

###

`ddl_utils.set_table_lock_settings(i_schema_name, i_table_name, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name        ddl_utils.non_null_text
i_table_name         ddl_utils.non_null_text
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY DEFINER`. Upserts the table's settings. The key is stored as given (not resolved against `pg_class`);
concurrent upserts on the same key can still
deadlock, so callers that need to survive that must retry.

### `ddl_utils.clear_table_lock_settings(i_schema_name, i_table_name)`

```sql
i_schema_name ddl_utils.non_null_text
i_table_name  ddl_utils.non_null_text
RETURNS void
```

`SECURITY DEFINER`. Deletes the table's row; a no-op when there is none.

### `ddl_utils.get_lock_settings(i_schema_name, i_table_name)`

```sql
i_schema_name ddl_utils.non_null_text
i_table_name  ddl_utils.non_null_text
RETURNS TABLE
(ddl_lock_timeout integer, sleep_time integer, statement_duration integer)
```

`STABLE`, `SECURITY INVOKER`. Resolves the effective settings for a table,
falling back table → schema → database. Raises `P0002` when nothing matches.

### `ddl_utils.add_columns(i_schema_name, i_table_name, i_column_names, i_column_types, i_default_values, i_nullable)`

```sql
i_schema_name   ddl_utils.non_null_text
i_table_name    ddl_utils.non_null_text
i_column_names  ddl_utils.non_empty_non_null_text_array
i_column_types  ddl_utils.non_empty_non_null_text_array
i_default_values ddl_utils.non_empty_text_array
i_nullable      ddl_utils.non_empty_non_null_boolean_array
RETURNS void
```

`SECURITY INVOKER`. Lock-aware wrapper: resolves the settings via
`ddl_utils.get_lock_settings` and delegates to `ddl_utils_lib.add_columns`.

### `ddl_utils.add_column(i_schema_name, i_table_name, i_column_name, i_column_type, i_nullable [, i_default_value])`

```sql
i_schema_name   ddl_utils.non_null_text
i_table_name    ddl_utils.non_null_text
i_column_name   ddl_utils.non_null_text
i_column_type   ddl_utils.non_null_text
i_nullable      ddl_utils.non_null_boolean
i_default_value text DEFAULT NULL
RETURNS void
```

`SECURITY INVOKER`. Single-column lock-aware wrapper; delegates to
`ddl_utils.add_columns`. Omit `i_default_value` to add a column with no default.

### `ddl_utils.drop_column(i_schema_name, i_table_name, i_column_name)`

```sql
i_schema_name ddl_utils.non_null_text
i_table_name  ddl_utils.non_null_text
i_column_name ddl_utils.non_null_text
RETURNS void
```

`SECURITY INVOKER`. Single-column convenience over `ddl_utils.drop_columns`.

### `ddl_utils.drop_columns(i_schema_name, i_table_name, i_column_names)`

```sql
i_schema_name  ddl_utils.non_null_text
i_table_name   ddl_utils.non_null_text
i_column_names ddl_utils.non_empty_non_null_text_array
RETURNS void
```

`SECURITY INVOKER`. Lock-aware wrapper; resolves the table's settings via
`get_lock_settings` and delegates to `ddl_utils_lib.drop_columns`.

### `ddl_utils.rename_column(i_schema_name, i_table_name, i_column_name, i_new_column_name)`

```sql
i_schema_name     ddl_utils.non_null_text
i_table_name      ddl_utils.non_null_text
i_column_name     ddl_utils.non_null_text
i_new_column_name ddl_utils.non_null_text
RETURNS void
```

`SECURITY INVOKER`. Lock-aware wrapper; resolves the table's settings via
`get_lock_settings` and delegates to `ddl_utils_lib.rename_column`.

### Column attributes

`ddl_utils.set_column_default`, `drop_column_default`, `drop_not_null`,
`set_column_statistics`, `set_column_storage`, `set_column_compression`,
`drop_expression`, `add_identity` and `drop_identity` are lock-aware wrappers
over the matching `ddl_utils_lib` helpers (same names). All are `SECURITY
INVOKER`, resolve the settings via `get_lock_settings`, and are metadata-only
(no scan or rewrite): they change the catalog, not the stored rows. The single
`SET NOT NULL` inverse is not offered because it scans.

### Constraints and tables

`ddl_utils.add_check_constraint`, `add_foreign_key`, `validate_constraint`,
`drop_constraint`, `rename_constraint`, `add_primary_key_using_index`,
`add_unique_constraint_using_index`, `rename_table` and
`set_table_storage_parameter` are lock-aware wrappers over the matching
`ddl_utils_lib` helpers (same names). `add_check_constraint` and
`add_foreign_key` emit `NOT VALID` (metadata-only); call `validate_constraint`
separately to enforce the constraint against existing rows (it scans but takes
only `SHARE UPDATE EXCLUSIVE`). The two `*_using_index` helpers attach a
pre-built unique index (`CREATE UNIQUE INDEX CONCURRENTLY`), which is
metadata-only when the index is valid.

## `ddl_utils_lib`

###

`ddl_utils_lib.alter_table(i_schema_name, i_table_name, i_alter_table_fragment, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name           ddl_utils.non_null_text
i_table_name            ddl_utils.non_null_text
i_alter_table_fragment  ddl_utils.non_null_text
i_ddl_lock_timeout      ddl_utils.non_negative_integer
i_sleep_time            ddl_utils.non_negative_integer
i_statement_duration    ddl_utils.non_negative_integer
RETURNS void
```

**Internal runner.** `SECURITY INVOKER`. Runs
`ALTER TABLE <schema>.<table> <fragment>` with the given lock/retry settings,
restoring the caller's `lock_timeout` on success. Rejects a blank fragment and
one containing `;`, `$` or a comment marker (a best-effort guard; the caller
already holds the privileges the fragment uses). This is the only routine that
executes dynamic SQL; prefer the structured helpers, which build the fragment
from validated identifiers/values.

### `ddl_utils_lib.has_top_level_comma(i_value)`

```sql
i_value text
RETURNS boolean
```

`IMMUTABLE`, `SECURITY INVOKER`. True when the value contains a comma outside
parentheses, brackets or a string literal. Used to reject defaults that could
append DDL clauses.

###

`ddl_utils_lib.add_columns(i_schema_name, i_table_name, i_column_names, i_column_types, i_default_values, i_nullable, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name        ddl_utils.non_null_text
i_table_name         ddl_utils.non_null_text
i_column_names       ddl_utils.non_empty_non_null_text_array
i_column_types       ddl_utils.non_empty_non_null_text_array
i_default_values     ddl_utils.non_empty_text_array
i_nullable           ddl_utils.non_empty_non_null_boolean_array
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY INVOKER`. Builds one `ADD COLUMN` clause per element and applies them
in a single `ALTER TABLE` (all-or-nothing) via `alter_table`. Validates array
lengths, blank names/types, that each type resolves to a single SQL type (`to_regtype`), and that no default has a
top-level comma.

###

`ddl_utils_lib.add_column(i_schema_name, i_table_name, i_column_name, i_column_type, i_nullable, i_default_value, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name        ddl_utils.non_null_text
i_table_name         ddl_utils.non_null_text
i_column_name        ddl_utils.non_null_text
i_column_type        ddl_utils.non_null_text
i_nullable           ddl_utils.non_null_boolean
i_default_value      text
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY INVOKER`. Single-column convenience over `ddl_utils_lib.add_columns`
with explicit lock settings. A NULL `i_default_value` means no `DEFAULT` clause.

###

`ddl_utils_lib.drop_column(i_schema_name, i_table_name, i_column_name, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name        ddl_utils.non_null_text
i_table_name         ddl_utils.non_null_text
i_column_name        ddl_utils.non_null_text
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY INVOKER`. Single-column convenience over `ddl_utils_lib.drop_columns`.

###

`ddl_utils_lib.drop_columns(i_schema_name, i_table_name, i_column_names, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name        ddl_utils.non_null_text
i_table_name         ddl_utils.non_null_text
i_column_names       ddl_utils.non_empty_non_null_text_array
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY INVOKER`. Builds one `DROP COLUMN` clause per element and applies them
in a single `ALTER TABLE` (all-or-nothing) via `ddl_utils_lib.alter_table`. Each
name is quoted with `%I`; a blank element is rejected with `22023`.

###

`ddl_utils_lib.rename_column(i_schema_name, i_table_name, i_column_name, i_new_column_name, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

```sql
i_schema_name        ddl_utils.non_null_text
i_table_name         ddl_utils.non_null_text
i_column_name        ddl_utils.non_null_text
i_new_column_name    ddl_utils.non_null_text
i_ddl_lock_timeout   ddl_utils.non_negative_integer
i_sleep_time         ddl_utils.non_negative_integer
i_statement_duration ddl_utils.non_negative_integer
RETURNS void
```

`SECURITY INVOKER`. Renames one column; both names are quoted with `%I` and
applied through `ddl_utils_lib.alter_table` with explicit lock settings.

### `ddl_utils_lib.set_column_default(i_schema_name, i_table_name, i_column_name, i_default_value, i_ddl_lock_timeout, i_sleep_time, i_statement_duration)`

`SECURITY INVOKER`. Sets a column default. `i_default_value` is raw SQL (quoted
literals must include their quotes); a top-level comma is rejected with `22023`.

### `ddl_utils_lib.drop_column_default(...)` / `ddl_utils_lib.drop_not_null(...)`

`SECURITY INVOKER`. Drop a column default or its `NOT NULL`, respectively; both
are metadata-only and take only a brief `ACCESS EXCLUSIVE` lock.

### `ddl_utils_lib.set_column_statistics(i_schema_name, i_table_name, i_column_name, i_statistics, settings...)`

`SECURITY INVOKER`. Sets the per-column statistics target. `i_statistics` must
be `-1` (reset) or between `0` and `10000`; any other value is rejected with
`22023`. Takes only `SHARE UPDATE EXCLUSIVE`.

### `ddl_utils_lib.set_column_storage(...)` / `ddl_utils_lib.set_column_compression(...)`

`SECURITY INVOKER`. Set a column's storage (`PLAIN`, `EXTERNAL`, `EXTENDED`,
`MAIN`) or compression (`pglz`, `lz4`, `default`); an unknown keyword is
rejected with `22023`. Both affect future writes only.

### `ddl_utils_lib.drop_expression(...)`

`SECURITY INVOKER`. Turns a generated column into a plain base column; a column
that is not a stored generated column raises `55000`.

### `ddl_utils_lib.add_identity(i_schema_name, i_table_name, i_column_name, i_generated, settings...)` / `ddl_utils_lib.drop_identity(..., i_if_exists, ...)`

`SECURITY INVOKER`. Add (`ALWAYS`/`BY DEFAULT`) or drop an identity. An invalid
generated mode is rejected with `22023`; `i_if_exists` controls `IF EXISTS`.

### `ddl_utils_lib.add_check_constraint(i_schema_name, i_table_name, i_constraint_name, i_check_expression, settings...)`

`SECURITY INVOKER`. Adds a `CHECK` constraint as `NOT VALID` (metadata-only).
The expression is raw SQL; a top-level comma is rejected with `22023`.

### `ddl_utils_lib.add_foreign_key(i_schema_name, i_table_name, i_constraint_name, i_column_names, i_referenced_schema_name, i_referenced_table_name, i_referenced_column_names, settings...)`

`SECURITY INVOKER`. Adds a foreign key as `NOT VALID` (no scan; takes `SHARE
ROW EXCLUSIVE` on both tables). The two column arrays must be non-empty and of
equal length; blank names are rejected with `22023`.

### `ddl_utils_lib.validate_constraint(i_schema_name, i_table_name, i_constraint_name, settings...)`

`SECURITY INVOKER`. Validates a constraint, scanning existing rows under
`SHARE UPDATE EXCLUSIVE` (does not block concurrent DML). Use it after the
`NOT VALID` helpers.

### `ddl_utils_lib.drop_constraint(...)` / `ddl_utils_lib.rename_constraint(..., i_new_constraint_name, ...)`

`SECURITY INVOKER`. Drop or rename a constraint; both are metadata-only.
`drop_constraint` does not use `IF EXISTS`, so a wrong name fails.

### `ddl_utils_lib.add_primary_key_using_index(i_schema_name, i_table_name, i_constraint_name, i_index_name, settings...)` / `ddl_utils_lib.add_unique_constraint_using_index(...)`

`SECURITY INVOKER`. Attach a pre-built unique index as a primary key or unique
constraint. Build the index with `CREATE UNIQUE INDEX CONCURRENTLY` first; the
attach is metadata-only when the index is valid.

### `ddl_utils_lib.rename_table(i_schema_name, i_table_name, i_new_table_name, settings...)`

`SECURITY INVOKER`. Renames a table within its schema; metadata-only.

### `ddl_utils_lib.set_table_storage_parameter(i_schema_name, i_table_name, i_parameter_name, i_parameter_value, settings...)`

`SECURITY INVOKER`. Sets one storage parameter accepted under `SHARE UPDATE
EXCLUSIVE` (`fillfactor`, the `autovacuum_*` / `toast.autovacuum_*` family and
`parallel_workers`); any other name is rejected with `22023`, so a
rewrite-inducing option cannot be reached through this helper.

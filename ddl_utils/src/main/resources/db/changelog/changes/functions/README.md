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

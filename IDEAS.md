# JDBScript Ideas

Not yet decided. An idea moves to [TODO.md](TODO.md) once it has a shape, or is struck through here
with a one-line reason.

## Typed query assertions

*2026-10-12. Status: to evaluate in `java-db-testing-compared`.*

Assert what the code under test did to the database, through the schema interface: e.g. "placing
an order inserted exactly one `orders` row and three `order_items`, and never touched `payments`",
written with the same `db.orders()` accessors as fixtures. A natural extension of
`assertDBHas` from state to behavior, and a guard against N+1 queries with JPA.

- **Why it could differentiate:** plain query counting already exists (datasource-proxy's
  `QueryCountHolder`, Hypersistence Utils' `SQLStatementCountValidator`, QuickPerf). Per-table,
  typed assertions need a typed model of the tables, which only jdbscript has.
- **Open questions:** how to wrap the application's `DataSource` (e.g. a Spring bean
  post-processor); attributing statements to tables (SQL parsing); JPA batching and flush timing;
  what the assertion API looks like.
- **Evaluate with:** the JPA app in `java-db-testing-compared`. Its order-placement and search
  code will show whether typed assertions are useful in practice, before any API design.
- **Side benefit:** the same proxy could report jdbscript's own statements and round trips,
  useful for the performance work, though only for self-improvement.

## Parametrized scripts

*Moved from TODO.md, 2026-10-12.* Scripts that take parameters. Open: what it adds over a lambda
capturing local variables, or a DSL sub-interface method with arguments (`engine.as(...)`).

## Annotation-based conversion

*Moved from TODO.md, 2026-10-12.* An annotation-driven option for type conversion. Possibly
obsolete: `.converter(...)` / `.disableDefaultConverters()` already cover custom mapping without
annotations.

## Exception policy

*Moved from TODO.md, 2026-10-12.* Which exceptions jdbscript should throw, as a general policy
(today: `JDBScriptException`, plus some `IllegalStateException`, `IllegalArgumentException` and
raw `RuntimeException` wrappers). Open design question.

## More DBMS support

*Moved from TODO.md, 2026-10-12.* Snowflake? Google BigQuery?

# JDBScript Ideas

Not yet decided. An idea moves to [TODO.md](TODO.md) once it has a shape, or is struck through here
with a one-line reason.

## Assertion block with counts and matchers

*2026-10-29. Status: needs more thinking. Source: `JDBSCRIPT_FINDINGS.md` in
`java-db-testing-compared` (no "exactly N", no empty-table assertion, no "about now").*

Mockito-like assertions: one `assertDB` block, each record with its own expected count, and
column values matched by matchers instead of equality.

```java
engine.assertDB(db -> {
    hasOnlyOne(db.orders().customerId(7).placedAt(aboutNow(Duration.ofSeconds(5)))
            .total(between(new BigDecimal("19"), new BigDecimal("20"))));
    hasExactly(5, db.orders().customerId(7));
    hasNot(db.orders().customerId(8), db.orders().customerId(9));
});
```

- **Mechanics:** `has*` and matchers are statics (one import, e.g. `JDBAssert.*`) that reach the
  current `assertDB` through a thread-local. A matcher pushes itself and returns a dummy value of
  the setter's type, which `ScriptHandler` pops on the setter call (Mockito's trick; simpler here,
  since a setter has one argument, so raw values and matchers can't mix). A record is registered
  when `db.orders()...` runs, so `has*(record)` only tags it with a count.
- **Matching:** translate to SQL where possible (`between` -> `BETWEEN`, precision / about ->
  range); free predicates (`argThat`) evaluated in Java on fetched rows, like LOBs today.
- **Open questions:**
  - Count with several records: per record or total? Proposal: `hasExactly` / `hasOnlyOne` take
    one record; only `has` (each matches >= 1) and `hasNot` (none matches) take several.
  - Names: `has`, `hasNot`, `hasOnlyOne`, `hasExactly(n)`, `hasAtLeast(n)`?
  - A record not wrapped in any `has*`: fail with a clear error (forgotten wrapper) rather than
    default to "at least one".
  - Empty table: a record with no columns matches every row, so `hasNot(db.payments())`.
  - `assertDBHas` / `assertDBHasNot` stay as shortcuts for `has` / `hasNot` on every record.
  - Misuse (matcher in `insertDB`, `has*` outside `assertDB`): descriptive error.
  - Matchers on converted columns (enums, custom converters) must convert their bounds.
  - Alternative to statics: `(db, a) -> a.hasOnlyOne(...)`, no thread-local, better autocomplete;
    but matchers must be static anyway.
- **Groundwork:** the v1.3.3 failure message reports every failing record and lets each column
  expectation describe itself, so matchers and counts can plug into the same reporting.

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

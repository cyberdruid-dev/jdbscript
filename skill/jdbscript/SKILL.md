---
name: jdbscript
description: Use when writing or editing JVM test code that seeds, resets, or asserts against a relational database with jdbscript (org.jdbscript) — defining IDBSchema/IDBRecord interfaces, building a JDBEngine, or calling resetDB/insertDB/updateDB/cleanupDB/assertDBHas/assertDBHasNot. Covers schema modeling, fixtures, defaults/generators, composition, and multi-DBMS quirks.
---

# jdbscript

Type-safe, fluent database seeding for JVM tests. Schema is modeled as plain Java interfaces;
`JDBEngine` executes fluent "scripts" against them to seed, reset, or assert on real database
state — no XML/JSON fixtures, no raw SQL strings.

## Core model

1. **Schema interface** extends `IDBSchema`, with one no-arg method per table returning that
   table's record interface.
2. **Record interface** extends `IDBSchema.IDBRecord`. Each fluent setter method returns `this`
   (well, the record type) so calls chain: `db.users().id(1L).username("alice")`.
3. **`JDBEngine<TSchema>`** built via `JDBEngine.builder(TSchema.class).dataSource(ds).build()`,
   used to run scripts: `engine.resetDB(...)`, `engine.insertDB(...)`, `engine.updateDB(...)`, `engine.cleanupDB()`,
   `engine.assertDBHas(...)`, `engine.assertDBHasNot(...)`.
4. A **script** is either a lambda (`Consumer<TSchema>` — `db -> { db.users()... ; }`) or a
   class that `implements TSchema` and populates rows in an instance initializer block. Rules for
   the class form:
   - If nested, it must be declared `static`.
   - It's normally `abstract` too, since it never overrides the interface's table-accessor
     methods (`users()`, `orders()`, ...) — plain Java requires that regardless of jdbscript.
   - It must have only a no-arg constructor (implicit or explicit).

## Critical rule: method name == column name, verbatim

```java
interface IOrderRecord extends IDBRecord {
    IOrderRecord user_id(Long userId); // -> column "user_id"
}
```

There is **no** camelCase→snake_case conversion and no annotation to override it. Name the Java
method exactly as the database column is named, even if that means a non-camelCase method name.
Do not "fix" `user_id` to `userId` — that silently breaks the mapping.

## When writing schema interfaces

- One record interface per table; one accessor method per table on the schema interface.
- Every setter returns the record type, enabling chaining.
- Put defaults/generators on the record interface itself via a `default void defaults(RecordTools
  tools)` method (see below) — not on the engine or schema.

## Seeding data

```java
engine.resetDB(db -> {                 // wipes schema's tables (FK-safe order), then inserts
    db.users().id(1L).username("alice").email("alice@example.com").active(true);
    db.orders().id(101L).user_id(1L).total_amount(49.99);
});

engine.insertDB(db -> {                // inserts without wiping existing data
    db.users().id(3L).username("charlie");
});
```

Prefer `resetDB` for a test's baseline arrange step; use `insertDB` when you need to interleave
inserts with actions under test (simulating events over time within one test).

```java
engine.updateDB(db -> db.users().id(1L).active(false));   // changes only `active` of user 1
```

Use `updateDB` to tweak a row of a shared baseline for one test, or to change data between actions
under test. The primary key columns (from DB metadata) select the row and must all be set; the other
set columns are updated; defaults are not applied. It fails if the row doesn't exist.

## Reusable / composable scripts

```java
public abstract class BaseUsersFixture implements IAppSchema {{
    users().id(1L).username("admin").active(true);
    users().id(2L).username("guest").active(true);
}};

engine.resetDB(BaseUsersFixture.class);

engine.resetDB(db -> {
    db.include(BaseUsersFixture.class); // or db.include(someLambdaScript)
    db.orders().id(200L).user_id(1L).total_amount(99.00);
});
```

A script is just Java — loops, `Random` (seed it, e.g. `new Random(42)`, for reproducible bulk
fixtures), conditionals — nothing special is needed to generate many rows.

## Defaults and generators (`RecordTools`)

```java
interface IUserRecord extends IDBRecord {
    IUserRecord id(Integer id);
    IUserRecord username(String username);
    IUserRecord email(String email);

    default void defaults(RecordTools tools) {
        int nextId = tools.nextIntId("user_id_seq", 1);
        id(nextId);
        username("user_" + nextId);
        email(tools.strValue("${username}@example.com")); // templated on already-set columns
    }
}
```

`defaults(...)` runs before explicit setters in the script are applied to fill in gaps — any
column already set by the caller wins over the default. Use `tools.nextIntId(key, start)` for
auto-incrementing IDs scoped by an arbitrary key, and `tools.strValue("${col}...")` to template a
string off another column already set on the same record.

## Assertions

Same fluent API, used to verify rather than insert:

```java
engine.assertDBHas(db -> db.users().username("alice").active(true));
engine.assertDBHasNot(db -> db.users().username("malory"));
```

Only the columns you set are checked — omitted columns are wildcards, not implied nulls.

**Prefer asserting on your own code's behavior** (call the real system under test, assert on
what it returns) over `assertDBHas`/`assertDBHasNot`. Reach for these only when nothing in your
own code exposes the state you need to check — e.g. a DB trigger's side effect that no query in
your app surfaces.

## Cleanup and FK ordering

`engine.cleanupDB()` deletes all rows from the schema's tables in FK-safe (child-before-parent)
order, auto-detected from the database's foreign keys — you don't need to sequence cleanup
yourself. A real cycle **between** two or more tables throws; if FK auto-detection can't
determine order at all (missing FK metadata, views standing in for tables), override it
explicitly:

```java
JDBEngine.builder(IAppSchema.class)
    .dataSource(dataSource)
    .tableDependencyOrder(List.of("users", "orders")) // parent-to-child; insert order
    .build();
```

List every table the schema interface declares, parent-to-child.

## Builder options worth knowing

- `.dataSource(ds)` or `.dataSource(() -> ds)` for lazy resolution.
- `.cacheStrategy(CacheStrategy.INSTANCE|GLOBAL|NONE)` — use `GLOBAL` when schema is static
  across the whole suite (faster), `NONE` when schema changes between tests (e.g. dynamic
  migrations).
- `.converter(new MyConverter())` to teach jdbscript a domain type it doesn't already handle (see
  `reference/examples.md`).

## Writing good fixtures

- A script should read like a DB script: one row per line — wrap only once a line gets too long.
  Don't split a record's chained setters across lines just because a formatter would.
- Set only the columns this specific test case actually cares about. Push everything else out of
  the fixture: irrelevant-but-required columns belong in `defaults(RecordTools)` (see above),
  and rows shared across tests belong in a common base fixture (see "Reusable / composable
  scripts" above), not repeated inline. A test's own script should be as short as the behavior
  it's arranging for — the shorter it is, the more obvious what's actually under test.

## Gotchas

- Column-name mismatches are the most common bug: a method named `userId` will try to write to a
  column literally called `userId`, not `user_id`.
- `resetDB` wipes tables in the schema interface, not the whole database — tables absent from the
  schema interface are untouched.
- Sequence values are reset to a high number (10000+) after insertion on Postgres, Oracle, DB2,
  and HSQLDB, specifically to avoid clashing with manually assigned IDs in later `insertDB` calls.

## Runnable references

See `reference/examples.md` in this skill directory for condensed, self-contained code snippets
covering each concern below — no network access required:

| Concern | Shows |
|---|---|
| Quickstart | Minimal schema + `resetDB`/`insertDB` arrange, then act/assert on real code |
| Class scripts & include | Class-based base fixture + `db.include(...)` composition |
| RecordTools defaults | `defaults(RecordTools)`, and explicit setters overriding defaults |
| Bulk scripting | Bulk row generation with a loop and a seeded `Random` |
| Custom converters | `IJDBTypeConverter` for a domain value type |
| Insert power | `insertDB` interleaved with actions under test |
| Testcontainers | Same schema/code as quickstart, against a real PostgreSQL container |
| Spring Boot | Dropped into `@SpringBootTest`, using the auto-configured `DataSource` bean |

For anything beyond that, the upstream project is at
[github.com/cyberdruid-dev/jdbscript](https://github.com/cyberdruid-dev/jdbscript) (README and
full runnable example modules under `examples/`).

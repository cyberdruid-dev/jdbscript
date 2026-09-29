# JDBScript

> **Type-safe, fluent database seeding and test data management for Java.**

[![Maven Central](https://img.shields.io/maven-central/v/org.jdbscript/jdbscript.svg)](https://central.sonatype.com/artifact/org.jdbscript/jdbscript)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![JDK](https://img.shields.io/badge/JDK-17%2B-green.svg)

---

## Overview

**JDBScript** is a lightweight, type-safe Java library designed to make database seeding, fixture management, and test data preparation simple, robust, and maintainable.

Instead of writing verbose raw SQL scripts or maintaining fragile DbUnit-style XML/JSON datasets, JDBScript lets you model your database tables and columns using standard Java interfaces. You can define test fixtures fluently with full IDE auto-completion, compile-time safety, dynamic defaults, and cross-DBMS compatibility.

## Contents

- [Key Features](#key-features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Usage Examples](#usage-examples)
- [Database Assertions](#database-assertions)
- [Migration Testing](#migration-testing)
- [Supported Databases](#supported-databases)
- [Known Limitations](#known-limitations)
- [Why not DbUnit?](#why-not-dbunit)
- [Roadmap & Contributing](#roadmap--contributing)

---

## Key Features

- **Type-Safe Schema Definitions**: Declare database tables and columns as Java interfaces with zero boilerplate.
- **Fluent & Expressive API**: Create single or multiple rows with chained method calls.
- **Flexible Script Formats**: Write scripts inline via lambdas (`db -> { ... }`) or encapsulate reusable datasets as static/abstract classes.
- **Script Composition**: Compose and reuse scripts using `db.include(...)`.
- **Smart Defaults & Generators**: Define default column values, auto-incrementing IDs (`RecordTools.nextIntId`), and templated strings (`RecordTools.strValue`).
- **Cleanups & Resets**: Easily wipe tables (`cleanupDB`) and reset state before or between tests. Tables are automatically deleted in the correct order based on foreign key dependencies.
- **Database Assertions**: Verify that specific records exist or do not exist in the database using the same fluent API.
- **Migration Testing**: Test what a Liquibase or Flyway migration does to existing rows: seed data in the pre-migration shape, migrate, then assert the post-migration shape (see [Migration Testing](#migration-testing)).
- **Metadata Caching**: Built-in caching for database metadata (tables, foreign keys) to speed up test execution.
- **Multi-DBMS Compatibility**: Built-in support for PostgreSQL, MySQL, MariaDB, Oracle, Microsoft SQL Server, H2, HSQLDB, IBM DB2, CockroachDB, SQLite, DuckDB, and Google Cloud Spanner.
- **Automatic Type Conversion**: Seamless handling of Java Enums, UUIDs, Dates, Timestamps, and binary data.
- **Sequence Management**: Cleanup restarts sequences and identity columns at exactly 10000, so manually assigned low IDs (1, 2, ...) never collide with generated ones (supported for PostgreSQL, CockroachDB, Oracle, DB2, HSQLDB, and DuckDB).

---

## Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.jdbscript</groupId>
    <artifactId>jdbscript</artifactId>
    <version>1.2.0</version>
    <scope>test</scope>
</dependency>
```

### Gradle

**Groovy DSL** (`build.gradle`):
```groovy
testImplementation 'org.jdbscript:jdbscript:1.2.0'
```

**Kotlin DSL** (`build.gradle.kts`):
```kotlin
testImplementation("org.jdbscript:jdbscript:1.2.0")
```

### Requirements

- **Java 17** or higher
- Standard JDBC `DataSource`

---

## Quick Start

### 1. Define Your Schema Interfaces

Define an interface extending `IDBSchema` representing your database schema, and record interfaces extending `IDBRecord` representing your tables:

```java
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;

public interface IAppSchema extends IDBSchema {
    IUserRecord users();

    IOrderRecord orders();

    interface IUserRecord extends IDBRecord {
        IUserRecord id(Long id);

        IUserRecord username(String username);

        IUserRecord email(String email);

        IUserRecord active(Boolean active);
    }

    interface IOrderRecord extends IDBRecord {
        IOrderRecord id(Long id);

        IOrderRecord user_id(Long userId);

        IOrderRecord total_amount(Double amount);
    }
}
```

Each fluent setter's method name is used verbatim as the SQL column name - that's why it's `user_id`, not `userId`. There's no camelCase-to-snake_case conversion or annotation to override it; name your methods exactly as the column is named in the database.

### 2. Initialize `JDBEngine`

Create an instance of `JDBEngine` using the builder:

```java
import org.jdbscript.JDBEngine;
import org.jdbscript.IJDBEngine;
import javax.sql.DataSource;

DataSource dataSource = ...;

IJDBEngine<IAppSchema> engine = JDBEngine.builder(IAppSchema.class)
    .dataSource(dataSource)
    .build();
```

#### Other Useful Options

The builder provides several other methods for fine-tuning the engine:

*   **Lazy DataSource**: Use `.dataSource(() -> getDataSource())` for lazy connection resolution.
*   **Metadata Caching**: Use `.cacheStrategy(...)` to speed up tests (see [Metadata Caching](#metadata-caching)).
*   **Schema Validation**: Use `.unmappedTableStrategy(...)` to control validation behavior (see [Schema Validation](#schema-validation)). Standard migration tables are ignored by default.
*   **Custom Converters**: Use `.converter(...)` to register custom type mappings (see [Custom Type Converters](#custom-type-converters)).
*   **Data Change Callback**: Use `.onDataChange(...)` to run code after the engine changes data (see [Data Change Callback](#data-change-callback)).
*   **Manual Table Order**: Use `.tableDependencyOrder(...)` to override auto-detected insert/cleanup order when FK auto-detection can't be relied on (see [Manual Table Order Override](#manual-table-order-override)).

---

## Usage Examples

Runnable example projects (JUnit, Testcontainers, Spring Boot, Liquibase/Flyway migrations) are in [`examples/`](examples).

### Inline Scripts (Lambdas)

Populate test records fluently:

```java
// Cleans up tables defined in the schema and inserts the records
engine.resetDB(db -> {
    db.users().id(1L).username("alice").email("alice@example.com").active(true); 
    db.users().id(2L).username("bob").email("bob@example.com").active(false); 
    db.orders().id(101L).user_id(1L).total_amount(49.99);
});
```

To insert records without wiping existing data, use `insertDB`:

```java
engine.insertDB(db -> {
    db.users().id(3L).username("charlie").email("charlie@example.com");
});
```

### Class-Based Reusable Scripts

For common test scenarios (e.g. standard reference data, base user sets), define static or abstract classes:

```java
public abstract class BaseUsersFixture implements IAppSchema {{
    users().id(1L).username("admin").email("admin@example.com").active(true);
    users().id(2L).username("guest").email("guest@example.com").active(true);
}}
```

The rows go in an instance initializer (the inner pair of braces). The class is `abstract` because it doesn't implement the schema's table methods; a nested class must also be `static`, and only a no-arg constructor is allowed.

Execute them directly:

```java
engine.resetDB(BaseUsersFixture.class);
```

### Including / Composing Scripts

Combine modular scripts into larger fixtures:

```java
engine.resetDB(db -> {
    // Include base dataset
    db.include(BaseUsersFixture.class);

    // Or include a lambda / Consumer script
    db.include(anotherCustomScript);

    // Add specific test records
    db.orders().id(200L).user_id(1L).total_amount(99.00);
});
```

### Defaults and Generators (`RecordTools`)

Provide default values directly within your record interfaces using Java default methods. You can also inject `RecordTools` to generate auto-incrementing IDs or template-based strings:

```java
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.RecordTools;

public interface IUserRecord extends IDBRecord {
    IUserRecord id(Integer id);

    IUserRecord username(String username);

    IUserRecord email(String email);

    default void defaults(RecordTools tools) {
        int nextId = tools.nextIntId("user_id_seq", 1);
        id(nextId);
        username("user_" + nextId);
        email(tools.strValue("${username}@example.com"));
    }
}
```

When invoking `db.users()`, any omitted columns automatically receive their configured defaults:

```java
engine.resetDB(db -> {
    db.users(); // id=1, username="user_1", email="user_1@example.com"
    db.users().username("custom_user"); // id=2, username="custom_user", email="custom_user@example.com"
});
engine.insertDB(db -> db.users()); // id=3: counters keep counting until the next cleanupDB/resetDB
```

### Updating Existing Rows

Change a few columns of rows that are already in the database, e.g. a shared baseline from `@BeforeMethod`, or data changed mid-test:

```java
engine.updateDB(db -> db.users().id(1L).active(false));
```

Each record's primary key (read from the database metadata) selects the row; every other column set in the script is updated, and nothing else is touched - defaults are not applied. The call fails if a row isn't found, the table has no primary key, or a record doesn't set every primary key column. Like `insertDB`, each call runs in one transaction, rolled back if any record fails.

### Database Cleanup

Purge all records from tables associated with the schema:

```java
engine.cleanupDB();
```

JDBScript automatically detects foreign key dependencies and deletes records in the correct order to avoid constraint violations. Cleanup also restarts sequences and identity columns at 10000 (see [Key Features](#key-features) for supported databases), so the IDs a test gets from the database are predictable. If a circular dependency **between two or more tables** is detected, an exception will be thrown. A table referencing itself (e.g. an `employees` table with a `manager_id` column pointing back to `employees`) is not treated as a cycle. See [Manual Table Order Override](#manual-table-order-override) for an escape hatch when auto-detection can't determine the right order at all.

### Data Change Callback

If the application under test caches database data, register a callback to invalidate that cache whenever the engine changes the data:

```java
IJDBEngine<IAppSchema> engine = JDBEngine.builder(IAppSchema.class)
    .dataSource(dataSource)
    .onDataChange(() -> appCache.clear())
    .build();
```

It runs once after each successful `resetDB`, `insertDB`, `updateDB`, or `cleanupDB` call. To register several callbacks, call `.onDataChange(...)` once per callback; they run in registration order. An exception thrown by a callback is wrapped in a `JDBScriptException` and rethrown from the data-changing call.

---

## Manual Table Order Override

By default, JDBScript auto-detects table dependencies from foreign keys to decide insert and cleanup order. If a table's real dependencies aren't visible that way (missing FK metadata, views standing in for tables, cyclic references), override the order explicitly instead:

```java
IJDBEngine<IAppSchema> engine = JDBEngine.builder(IAppSchema.class)
    .dataSource(dataSource)
    .tableDependencyOrder(List.of("users", "orders"))
    .build();
```

List every table your schema interface declares, parent-to-child. Records are inserted in exactly this order; `cleanupDB()` cleans them up in the exact reverse. Matching is case-insensitive, and any extra entries not declared by the schema interface are ignored. A schema-interface table missing from the list throws an exception - on first use of the engine (the first `insertDB`/`cleanupDB`/`assertDBHas`/`assertDBHasNot` call), not at `.build()` time, keeping engine construction itself lazy. Schema-vs-database validation (missing/unmapped tables) is unaffected by this override - it always runs against the real database, regardless of whether ordering is auto-detected or manual.

---

## Database Assertions

Verify the state of your database using the same fluent API used for seeding:

```java
// Assert that specific records exist
engine.assertDBHas(db -> {
    db.users().username("alice").active(true);
});

// Assert that specific records do not exist
engine.assertDBHasNot(db -> {
    db.users().username("malory");
});
```

---

## Migration Testing

Liquibase and Flyway check that a migration applies; `JDBMigrationEngine` tests what it does to data that's already there, such as a changeset that backfills a new column from existing rows. You describe the schema on both sides of the migration with two interfaces, then:

```java
JDBMigrationEngine<IBeforeSchema, IAfterSchema> migration =
    JDBMigrationEngine.builder(IBeforeSchema.class, IAfterSchema.class)
        .dataSource(dataSource)
        .migrations("db/changelog.yaml")   // Liquibase changelog, or a Flyway location
        .build();

// Arrange: migrate up to the point right before the migration under test, seed old-shape rows
migration.migrateTo("before-full-name-backfill");
migration.before().insertDB(db -> {
    db.person().id(1).first_name("Ada").last_name("Lovelace");
});

// Act: run just the migration under test
migration.migrateTo("after-full-name-backfill");

// Assert: check the rows in the new shape
migration.after().assertDBHas(db -> db.person().id(1).full_name("Ada Lovelace"));
```

- **Markers**: `migrateTo(...)` takes a Liquibase tag, or a Flyway version (e.g. `"3"`). There's deliberately no "migrate everything" method: a test bounded by markers keeps testing the same migration as more are added later.
- **Tool detection**: `.migrations(path)` picks Liquibase or Flyway, whichever is on the classpath. If both are, use `.migrator(new LiquibaseMigrator(path))` or `.migrator(new FlywayMigrator(path))` instead.
- **Dependencies**: `liquibase-core` and `flyway-core` are optional dependencies of jdbscript, so add the one you use to your project yourself.
- **Engines**: `before()` and `after()` are regular engines sharing the builder's settings, always with `CacheStrategy.NONE` since the schema changes between them. Use `.beforeEngine(...)`/`.afterEngine(...)` to configure one side only.
- **Reset**: `reset()` wipes everything the migration tool manages, to start a test from a blank database. It's never called automatically.

Runnable versions: [`examples/09-liquibase-data-migration`](examples/09-liquibase-data-migration) and [`examples/10-flyway-data-migration`](examples/10-flyway-data-migration).

---

## Metadata Caching

To improve performance across multiple tests, JDBScript supports different metadata caching strategies:

- `CacheStrategy.INSTANCE`: Cache metadata for the lifetime of the `JDBEngine` instance. **This is the default.**
- `CacheStrategy.GLOBAL`: Cache metadata globally across all `JDBEngine` instances. Recommended if the database schema is static throughout the test suite.
- `CacheStrategy.NONE`: Disable caching. Recommended if the database schema changes between tests (e.g., dynamic migrations).

---

## Schema Validation

On first use (the first `insertDB`/`resetDB`/`cleanupDB`/assert call, not at `.build()` time), `JDBEngine` validates that all tables defined in your Java interface exist in the database. You can also configure how it handles tables that exist in the database but are *not* defined in your interface:

- `unmappedTableStrategy(ValidationStrategy.LOG_WARN)`: Log a warning (default).
- `unmappedTableStrategy(ValidationStrategy.LOG_ERROR)`: Log an error.
- `unmappedTableStrategy(ValidationStrategy.FAIL)`: Throw an exception.

Use `suppressUnmappedTable(String...)` to ignore specific custom tables. By default, standard migration tables like `flyway_schema_history` or `databasechangelog` are automatically ignored (`suppressDefaultUnmappedTables(true)`).

---

## Custom Type Converters

JDBScript comes with default converters for common types like Enums, UUIDs, and Dates. You can add your own by implementing `IJDBTypeConverter` and registering it with the `.converter(...)` builder method:

```java
IJDBEngine<IAppSchema> engine = JDBEngine.builder(IAppSchema.class)
    .dataSource(dataSource)
    .converter(new MyCustomConverter())
    .converter(new AnotherConverter())
    .build();
```

**Note:** `.converter(...)` **adds** to the default set rather than replacing it — call it once per converter to register several. Converters are tried in registration order, so a converter you add only gets a chance to run for types not already handled by an earlier one; the built-in defaults are checked first. To have your own converter take priority over a default for the same type (or to disable default conversion entirely), call `.disableDefaultConverters()` first:

```java
IJDBEngine<IAppSchema> engine = JDBEngine.builder(IAppSchema.class)
    .dataSource(dataSource)
    .disableDefaultConverters()
    .converter(new MyCustomEnumConverter())
    .build();
```

---

## Supported Databases

Every release is tested in CI against each database below, and on JDK 17, 21, and 25. Test counts differ because database-specific tests are skipped where they don't apply:

| Database | Tested Versions | Tests |
| :--- | :--- | :---: |
| **PostgreSQL** | `9.x`, `12.x`, `16.x`, `17.x`, `18.x` | ![Passed](https://img.shields.io/badge/294-passing-success?style=flat-square) |
| **MySQL** | `5.x`, `8.x`, `9.x` | ![Passed](https://img.shields.io/badge/291-passing-success?style=flat-square) |
| **MariaDB** | `10.x`, `11.x`, `12.x` | ![Passed](https://img.shields.io/badge/291-passing-success?style=flat-square) |
| **Oracle** | `Oracle Free 23c` | ![Passed](https://img.shields.io/badge/293-passing-success?style=flat-square) |
| **Microsoft SQL Server** | `2022` | ![Passed](https://img.shields.io/badge/291-passing-success?style=flat-square) |
| **IBM DB2** | Latest | ![Passed](https://img.shields.io/badge/296-passing-success?style=flat-square) |
| **CockroachDB** | Latest | ![Passed](https://img.shields.io/badge/294-passing-success?style=flat-square) |
| **Google Cloud Spanner** | Emulator | ![Passed](https://img.shields.io/badge/288-passing-success?style=flat-square) |
| **H2** | `2.4.x` | ![Passed](https://img.shields.io/badge/291-passing-success?style=flat-square) |
| **HSQLDB** | `2.7.x` | ![Passed](https://img.shields.io/badge/293-passing-success?style=flat-square) |
| **SQLite** | `3.53.x` | ![Passed](https://img.shields.io/badge/291-passing-success?style=flat-square) |
| **DuckDB** | `1.2.x` | ![Passed](https://img.shields.io/badge/253-passing-success?style=flat-square) |

---

## Known Limitations

- **Circular foreign keys between tables** are rejected by auto-detection; set the order yourself with [`.tableDependencyOrder(...)`](#manual-table-order-override). A table referencing itself is fine, but its rows are inserted in the order you declare them, so declare parent rows first.
- **Cleanup deletes all rows** from every table in the schema interface. Tests that run in parallel against the same database will interfere with each other.

---

## Why not DbUnit?

JDBScript is an alternative to [DbUnit](https://www.dbunit.org/) for preparing and checking database state in Java tests. The differences:

- **Test data is Java code, not XML/CSV/YAML datasets.** It's type-checked and auto-completed by the IDE, and renaming a column is one refactoring instead of a search-and-replace across dataset files.
- **Only the columns a test cares about.** `defaults()` fills in the rest, so a new `NOT NULL` column means one change in the record interface instead of edits across every dataset.
- **Composable.** Scripts include other scripts, and loops or helper methods generate rows; no dataset files to copy and keep in sync.
- **Assertions use the same API.** `assertDBHas` checks only the columns you list, with no expected-dataset files or column filters.
- **Insert and cleanup order come from the foreign keys**, and cleanup resets sequences, with no table-order configuration.
- **Migration testing** for Liquibase and Flyway: seed data in the old schema, migrate, assert on the new one.

DbUnit remains a reasonable choice if you already maintain a large set of XML datasets, or need to export existing database content into a dataset.

---

## Roadmap & Contributing

See [TODO.md](TODO.md) for planned features, upcoming enhancements, and known items.

---

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

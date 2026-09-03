# JDBScript

> **Type-safe, fluent database seeding and test data management for Java.**

[![Maven Central](https://img.shields.io/maven-central/v/org.jdbscript/jdbscript.svg)](https://central.sonatype.com/artifact/org.jdbscript/jdbscript)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![JDK](https://img.shields.io/badge/JDK-17%2B-green.svg)]()

---

## Overview

**JDBScript** is a lightweight, type-safe Java library designed to make database seeding, fixture management, and test data preparation simple, robust, and maintainable.

Instead of writing verbose raw SQL scripts or maintaining fragile XML/JSON datasets, JDBScript lets you model your database tables and columns using standard Java interfaces. You can define test fixtures fluently with full IDE auto-completion, compile-time safety, dynamic defaults, and cross-DBMS compatibility.

---

## Key Features

- **Type-Safe Schema Definitions**: Declare database tables and columns as Java interfaces with zero boilerplate.
- **Fluent & Expressive API**: Create single or multiple rows with chained method calls.
- **Flexible Script Formats**: Write scripts inline via lambdas (`db -> { ... }`) or encapsulate reusable datasets as static/abstract classes.
- **Script Composition**: Compose and reuse scripts using `db.include(...)`.
- **Smart Defaults & Generators**: Define default column values, auto-incrementing IDs (`RecordTools.nextIntId`), and templated strings (`RecordTools.strValue`).
- **Cleanups & Resets**: Easily wipe tables (`cleanupDB`) and reset state before or between tests. Tables are automatically deleted in the correct order based on foreign key dependencies.
- **Database Assertions**: Verify that specific records exist or do not exist in the database using the same fluent API.
- **Metadata Caching**: Built-in caching for database metadata (FKs, columns) to speed up test execution.
- **Multi-DBMS Compatibility**: Built-in support for PostgreSQL, MySQL, MariaDB, Oracle, Microsoft SQL Server, H2, HSQLDB, IBM DB2, CockroachDB, SQLite, and DuckDB.
- **Automatic Type Conversion**: Seamless handling of Java Enums, UUIDs, Dates, Timestamps, and binary data.
- **Sequence Management**: Automatically resets database sequences to a high value (e.g., 10000+) after insertion to prevent primary key conflicts with manually assigned IDs (supported for PostgreSQL, Oracle, DB2, and HSQLDB).

---

## Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.jdbscript</groupId>
    <artifactId>jdbscript</artifactId>
    <version>1.1.0</version>
    <scope>test</scope>
</dependency>
```

### Gradle

**Groovy DSL** (`build.gradle`):
```groovy
testImplementation 'org.jdbscript:jdbscript:1.1.0'
```

**Kotlin DSL** (`build.gradle.kts`):
```kotlin
testImplementation("org.jdbscript:jdbscript:1.1.0")
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
*   **Manual Table Order**: Use `.tableDependencyOrder(...)` to override auto-detected insert/cleanup order when FK auto-detection can't be relied on (see [Manual Table Order Override](#manual-table-order-override)).

---

## Usage Examples

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
}};
```

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
```

### Database Cleanup

Purge all records from tables associated with the schema:

```java
engine.cleanupDB();
```

JDBScript automatically detects foreign key dependencies and deletes records in the correct order to avoid constraint violations. If a circular dependency **between two or more tables** is detected, an exception will be thrown. A table referencing itself (e.g. an `employees` table with a `manager_id` column pointing back to `employees`) is not treated as a cycle. See [Manual Table Order Override](#manual-table-order-override) for an escape hatch when auto-detection can't determine the right order at all.

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

## Metadata Caching

To improve performance across multiple tests, JDBScript supports different metadata caching strategies:

- `CacheStrategy.INSTANCE`: Cache metadata for the lifetime of the `JDBEngine` instance. **This is the default.**
- `CacheStrategy.GLOBAL`: Cache metadata globally across all `JDBEngine` instances. Recommended if the database schema is static throughout the test suite.
- `CacheStrategy.NONE`: Disable caching. Recommended if the database schema changes between tests (e.g., dynamic migrations).

---

## Schema Validation

When `JDBEngine` is initialized, it validates that all tables defined in your Java interface exist in the database. You can also configure how it handles tables that exist in the database but are *not* defined in your interface:

- `unmappedTableStrategy(ValidationStrategy.LOG_WARN)`: Log a warning (default).
- `unmappedTableStrategy(ValidationStrategy.LOG_ERROR)`: Log an error.
- `unmappedTableStrategy(ValidationStrategy.FAIL)`: Throw an exception.

Use `suppressUnmappedTable(String...)` to ignore specific custom tables. By default, standard migration tables like `flyway_schema_history` or `databasechangelog` are automatically ignored (`suppressDefaultUnmappedTables(true)`).

---

## Custom Type Converters

JDBScript comes with default converters for common types like Enums, UUIDs, and Dates. You can add your own using the `.converter(...)` builder method:

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

### 🧪 Supported Databases & Compatibility Matrix

`jdbscript` is continuously validated against 17+ database engines via automated integration test suites:

| Database Engine | Tested Versions                       | Compatibility Status |
| :--- |:--------------------------------------| :---: |
| **PostgreSQL** | `9.x`, `12.x`, `16.x`, `17.x`, `18.x`| ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **MySQL** | `5.x`, `8.x`, `9.x`                   | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **MariaDB** | `10.x`, `11.x`, `12.x`                | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **Oracle** | `Oracle Free 23c`                     | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **Microsoft SQL Server**| `2022`                                | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **IBM DB2** | Latest                                | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **CockroachDB** | Latest                                | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **H2** | `2.4.x`                               | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **HSQLDB** | `2.7.x`                            | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **SQLite** | Standard JDBC                         | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |
| **DuckDB** | Latest                                | ![Passed](https://img.shields.io/badge/196%2F196-passing-success?style=flat-square) |

---

## Roadmap & Contributing

See [TODO.md](TODO.md) for planned features, upcoming enhancements, and known items.

---

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

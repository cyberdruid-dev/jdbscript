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
- **Cleanups & Resets**: Easily wipe tables (`cleanupDB`) and reset state before or between tests.
- **Multi-DBMS Compatibility**: Built-in support for PostgreSQL, MySQL, MariaDB, Oracle, Microsoft SQL Server, H2, HSQLDB, IBM DB2, and SQLite.
- **Automatic Type Conversion**: Seamless handling of Java Enums, UUIDs, Dates, Timestamps, and binary data.

---

## Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.jdbscript</groupId>
    <artifactId>jdbscript</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### Gradle

**Groovy DSL** (`build.gradle`):
```groovy
testImplementation 'org.jdbscript:jdbscript:1.0.0'
```

**Kotlin DSL** (`build.gradle.kts`):
```kotlin
testImplementation("org.jdbscript:jdbscript:1.0.0")
```

### Requirements

- **Java 17** or higher
- Standard JDBC `DataSource`

---

## Quick Start

### 1. Define Your Schema Interfaces

Define an interface extending `IDbSchema` representing your database schema, and record interfaces extending `IDBRecord` representing your tables:

```java
import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;

public interface IAppSchema extends IDbSchema {
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

Create an instance of `JDBEngine` by supplying your `DataSource` and schema class:

```java
import org.jdbscript.JDBEngine;
import org.jdbscript.IJDBEngine;
import javax.sql.DataSource;

DataSource dataSource = ...; // e.g., HikariDataSource, Spring DataSource, etc.

IJDBEngine<IAppSchema> engine = new JDBEngine<>(dataSource, IAppSchema.class);
```

You can also pass a `Supplier<DataSource>` for lazy connection resolution:

```java
IJDBEngine<IAppSchema> engine = new JDBEngine<>(() -> getDataSource(), IAppSchema.class);
```

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
import org.jdbscript.IDbSchema.IDBRecord;
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

Purge all tables associated with the schema:

```java
engine.cleanupDB();
```

---

## Supported Databases

JDBScript automatically detects the target database type from the JDBC connection URL:

| DBMS | Driver / JDBC URL Prefix |
| :--- | :--- |
| **PostgreSQL** | `jdbc:postgresql:` |
| **MySQL** | `jdbc:mysql:` |
| **MariaDB** | `jdbc:mariadb:` |
| **Oracle** | `jdbc:oracle:` |
| **Microsoft SQL Server** | `jdbc:sqlserver:` |
| **H2 Database** | `jdbc:h2:` |
| **HSQLDB** | `jdbc:hsqldb:` |
| **IBM DB2** | `jdbc:db2:` |
| **SQLite** | `jdbc:sqlite:` |

---

## Roadmap & Contributing

See [TODO.md](TODO.md) for planned features, upcoming enhancements, and known items.

---

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

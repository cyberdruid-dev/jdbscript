# JDBScript v1.3.0 — Release Notes

JDBScript `1.3.0` is here! 🎉

This release is **battle-tested by real-world migration**: it is driven directly by the end-to-end migration of a large enterprise project (in continuous active development for over 9 years) from JDBScript's internal predecessor to this library. Real-world edge cases, multi-database quirks, and developer ergonomics encountered across thousands of tests directly shaped the features and fixes in this release.

---

## What's New & Highlights

### 🛠️ In-Place Row Updates with `updateDB(...)`
Update existing database rows in place without needing to re-seed or truncate tables. Perfect for tweaking baseline fixtures before running specific test cases:

```java
engine.updateDB(db -> {
    db.users().id(1L).active(false);
});
```

### 🧩 Domain DSLs & Custom Script Mixins with `engine.as(...)`
Create reusable, domain-specific test helpers and composite entity macros by extending your schema interface:

```java
public interface IOrderDSL extends IAppSchema {
    default void createCustomerWithOrders(long customerId, String name, double... amounts) {
        customers().id(customerId).name(name);
        for (int i = 0; i < amounts.length; i++) {
            orders().id(customerId * 100 + i).customer_id(customerId).total(amounts[i]);
        }
    }
}

// In your tests:
engine.as(IOrderDSL.class).insertDB(db -> {
    db.createCustomerWithOrders(101L, "Alice", 29.99, 99.00);
});
```

### 🔒 Engine-Scoped Sequence & ID Generation
Auto-incrementing counters and `RecordTools` sequence state are now isolated per `IJDBEngine` instance, ensuring deterministic behavior in parallel test suites.

---

## Database Fixes & Improvements

- **PostgreSQL**: Added robust type mapping and conversion fixes for `bytea` and `oid` binary columns.
- **Oracle & IBM DB2**: Improved sequence reset handling and filtered out internal temporary/system tables from schema reflections.
- **Cross-DBMS Compatibility**: Comprehensive verification against PostgreSQL (9–18), MySQL (5, 8, 9), MariaDB, Oracle Free 23c, MSSQL 2022, DB2, CockroachDB, Google Cloud Spanner, H2, HSQLDB, SQLite, and DuckDB.

---

## Documentation & Examples

- **11 Standalone Runnable Example Modules**: Covering Quickstart, Reusable Class Fixtures, Scripting Power, Custom Converters, Testcontainers, Spring Boot, Liquibase, Flyway data migrations, and Domain DSLs (`11-domain-dsl-and-helpers`).
- **Updated Agent Skill**: Enhanced AI assistant documentation with modern `1.3.0` block-lambda formatting and patterns.

---

## Installation

### Maven
```xml
<dependency>
    <groupId>org.jdbscript</groupId>
    <artifactId>jdbscript</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

### Gradle
```groovy
testImplementation 'org.jdbscript:jdbscript:1.3.0'
```

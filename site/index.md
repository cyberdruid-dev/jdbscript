---
layout: home

hero:
  name: "JDBScript"
  text: "Database Test State Setup"
  tagline: "Type-safe, zero-boilerplate database test fixtures for modern Java."
  actions:
    - theme: brand
      text: Maven Central (v1.3.0)
      link: https://central.sonatype.com/artifact/org.jdbscript/jdbscript/1.3.0
    - theme: alt
      text: GitHub Repository
      link: https://github.com/cyberdruid-dev/jdbscript

features:
  - title: Compile-Time Safety
    details: Declare tables and columns as standard Java interfaces. Catch typos and schema breakages during compilation.
  - title: Zero XML & Raw SQL
    details: Replace verbose SQL scripts and fragile XML/JSON datasets with clean, expressive, fluent Java builders.
  - title: Auto FK-Ordering
    details: Automatic foreign key dependency resolution handles correct insert and cleanup order without manual sorting.
  - title: Multi-DBMS Compatibility
    details: Write fixtures once and run them transparently across PostgreSQL, MySQL, Oracle, SQL Server, SQLite, DuckDB, H2, and more.
---

## Why JDBScript?

Setting up and verifying relational database fixtures in Java tests has traditionally been painful and fragile. **JDBScript** bridges this gap:

- **Compile-Time Checking**
  - *Problem*: Raw SQL strings and XML/JSON datasets fail at runtime when schemas change, offering zero compiler feedback or IDE safety.
  - *Solution*: Schemas are defined as typed Java interfaces. Renaming a column or changing a type triggers compile errors immediately.
- **No XML or Raw SQL**
  - *Problem*: DbUnit-style XML datasets are tedious to maintain, difficult to refactor, and decouple data definitions from your code.
  - *Solution*: Construct test datasets using fluent Java syntax, full IDE autocomplete, reusable fixtures, and smart default generators.
- **Automatic FK-Ordering**
  - *Problem*: Inserting or cleaning up dependent records requires manually managing topological order to avoid foreign key constraint violations.
  - *Solution*: JDBScript introspects foreign key metadata automatically, guaranteeing correct insertion order and safe reverse teardown.
- **Multi-DBMS Compatibility**
  - *Problem*: Raw SQL fixtures and DB-specific tools break when running tests across different DBMSs (e.g., in-memory databases locally vs PostgreSQL/Oracle in CI).
  - *Solution*: A single unified Java fixture runs transparently across 12+ supported databases including PostgreSQL, MySQL, Oracle, SQL Server, SQLite, DuckDB, and H2 with automatic dialect and sequence handling.

---

## JDBScript vs DbUnit

While DbUnit established dataset-driven testing in Java, maintaining external XML/YAML datasets imposes recurring maintenance overhead as applications evolve.

| Dimension | DbUnit Datasets | JDBScript |
| :--- | :--- | :--- |
| **Schema Drift** | Silent failures at test runtime | Immediate compile errors in the IDE |
| **Refactoring** | Fragile text search across external files | Automated IDE rename and reference updates |
| **Fixture Boilerplate** | Must specify all non-null columns per row | Smart defaults generate IDs and non-essential columns |
| **Type Safety** | Stringly-typed data; manual converters for Enums/UUIDs | Native Java types, Enums, and automatic JDBC conversion |
| **Sequence Management** | Manual sequence reset scripts to avoid ID collisions | Automatic sequence restarts preventing ID collisions |

---

## Quickstart

Add the `jdbscript` dependency to your project's test scope:

::: code-group

```xml [Maven (pom.xml)]
<dependency>
    <groupId>org.jdbscript</groupId>
    <artifactId>jdbscript</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

```kotlin [Gradle (build.gradle.kts)]
testImplementation("org.jdbscript:jdbscript:1.3.0")
```

```groovy [Gradle (build.gradle)]
testImplementation 'org.jdbscript:jdbscript:1.3.0'
```

:::

---

## Code Example

### 1. Declare Your Schema Interface

Define lightweight interfaces extending `IDBSchema` and `IDBRecord` to reflect your tables and columns:

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

### 2. Seed and Reset Test State with `resetDB`

Initialize `JDBEngine` and wipe tables & insert fixtures in a single fluent call:

```java
import org.jdbscript.JDBEngine;
import org.jdbscript.IJDBEngine;
import javax.sql.DataSource;

DataSource dataSource = getDataSource();
IJDBEngine<IAppSchema> engine = JDBEngine.builder(IAppSchema.class)
    .dataSource(dataSource)
    .build();

// Wipes schema tables and inserts records in FK-safe order
engine.resetDB(db -> {
    db.users().id(1L).username("alice").email("alice@example.com").active(true);
    db.users().id(2L).username("bob").email("bob@example.com").active(false);
    db.orders().id(1001L).user_id(1L).total_amount(59.99);
});
```

### 3. Verify Database State with `assertDB`

Assert that expected records exist or do not exist using the same intuitive syntax:

```java
// Assert that records exist in the database
engine.assertDBHas(db -> {
    db.users().username("alice").active(true);
    db.orders().user_id(1L).total_amount(59.99);
});

// Assert that records do not exist
engine.assertDBHasNot(db -> {
    db.users().username("charlie");
});
```

---

## Recipes

Runnable, self-contained example projects demonstrating real-world usage patterns across different stacks:

### Frameworks & Ecosystem
* **[Spring Boot (`08-springboot`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/08-springboot/src/test/java/org/jdbscript/examples/springboot/UserRepositorySpringBootTest.java)** — Using auto-configured `DataSource` and clearing `@Cacheable` caches via `onDataChange`.
* **[Testcontainers (`07-testcontainers`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/07-testcontainers/src/test/java/org/jdbscript/examples/testcontainers/TestcontainersTest.java)** — Running identical schema fixtures against a real PostgreSQL container.
* **[Kotlin DSL (`12-kotlin-dsl`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/12-kotlin-dsl/src/test/kotlin/org/jdbscript/examples/kotlindsl/KotlinDslTest.kt)** — Idiomatic Kotlin extensions, receiver lambdas (`engine.insert { ... }`), and reified builders.

### Migration Testing
* **[Liquibase Data Migration (`09-liquibase-data-migration`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/09-liquibase-data-migration/src/test/java/org/jdbscript/examples/liquibasedatamigration/PersonFullNameBackfillMigrationTest.java)** — Testing changeset row transformations by seeding pre-migration shape and asserting post-migration state.
* **[Flyway Data Migration (`10-flyway-data-migration`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/10-flyway-data-migration/src/test/java/org/jdbscript/examples/flywaydatamigration/PersonFullNameBackfillMigrationTest.java)** — Testing Flyway version migrations with intermediate state assertions.

### Core Patterns & Composition
* **[Quickstart (`01-quickstart`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/01-quickstart/src/test/java/org/jdbscript/examples/quickstart/QuickstartTest.java)** — Standard JUnit 5 arrange-act-assert pattern against real application code.
* **[Class Scripts & Composition (`02-class-scripts-and-include`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/02-class-scripts-and-include/src/test/java/org/jdbscript/examples/classscripts/ClassScriptsAndIncludeTest.java)** — Reusable base fixtures, `db.include(...)`, and mid-test `updateDB`.
* **[RecordTools & Defaults (`03-recordtools-defaults`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/03-recordtools-defaults/src/test/java/org/jdbscript/examples/defaults/RecordToolsDefaultsTest.java)** — Auto-incrementing sequences, templated strings, and smart defaults.
* **[Scripting Power (`04-scripting-power`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/04-scripting-power/src/test/java/org/jdbscript/examples/scripting/BulkScriptingTest.java)** — Loops, programmatic bulk data generation, and deterministic random seeds.
* **[Interleaved Insertion (`06-insert-power`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/06-insert-power/src/test/java/org/jdbscript/examples/insertpower/InsertPowerTest.java)** — Simulating multi-step time series and state progressions with `insertDB`.

### Advanced Modeling
* **[Domain DSLs & Aggregates (`11-domain-dsl-and-helpers`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/11-domain-dsl-and-helpers/src/test/java/org/jdbscript/examples/domaindsl/DomainDslAndHelpersTest.java)** — Expressive domain helpers with `engine.as(...)` for multi-table hierarchies.
* **[Custom Converters (`05-custom-converters`)](https://github.com/cyberdruid-dev/jdbscript/blob/main/examples/05-custom-converters/src/test/java/org/jdbscript/examples/converters/CustomConvertersTest.java)** — Mapping rich domain types (e.g. `Money`) via `IJDBTypeConverter`.

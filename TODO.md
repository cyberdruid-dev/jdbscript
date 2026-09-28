# JDBScript TODO & Roadmap

### Goals
* Simple to use
* Robust (across different DBMS & JDK)
* Explainability
  * proper logging
* Extensible(?)

## Open

#### Schema & Column Validation
* [ ] Validate an `IDBRecord` interface's declared column methods against the DB table's actual
  columns - same idea as `SchemaValidator`'s table check, one level deeper. Today a misspelled/
  wrong-case fluent setter surfaces as a raw JDBC `SQLException` at insert time, not a clear
  jdbscript error.
  * reuse `SchemaValidator.findRecordMethods()`'s reflection approach (`clazz.getMethods()`,
    filter Object/default/`defaults`), just applied to an `IDBRecord` subtype instead of the
    schema interface
  * one direction only: interface declares a column the DB doesn't have -> always fail. Unlike
    tables, DB columns absent from the interface are normal (audit columns, unused optional
    fields) and shouldn't warn - no `unmappedTableStrategy`-style toggle needed
  * validate lazily per table, on first use of that record type; reuse `CacheStrategy` so it's
    not a `DatabaseMetaData.getColumns()` call per insert
  * supersedes the old, vaguer "`engine.verifySchema()`" idea - table-level validation already
    ships automatically on first use; this is the column-level counterpart
* [ ] Explore table/column name case sensitivity - generated SQL inserts identifiers unquoted, so
  an exact match already depends on each DBMS's own case-folding rules; decide if/how jdbscript
  should help here beyond documenting it (done for columns - see `IDBRecord`'s javadoc)

#### Type Conversion
* [ ] Warn (or leave to the DBMS) when a Java value has more precision than the DB column
  supports (e.g. `Date` vs a DB `DATE` column; MariaDB + `assertDBHas()` on timestamps)
  * alternative: leave it to the DBMS - MySQL already throws on this
* [ ] Custom types - needs a dedicated test
* [ ] Per-field custom converters (e.g. a UUID stored differently in different columns) - check
  how much of this `.converter(...)` already covers before starting
* [ ] Annotation-based conversion option - reconsider whether this is still needed now that
  `.converter(...)` / `.disableDefaultConverters()` cover custom mapping without annotations

#### Defaults & Generated IDs
* [ ] `defaults()` should work from class-based scripts too, not just lambdas
* [ ] Defaults: templating
* [ ] Gotcha (found while writing examples/03-recordtools-defaults): `RecordTools.nextIntId`/
  `nextLongId` advances its counter every time `defaults()` runs for a record, even when the
  resulting `id(...)` call is a no-op because that column was already set explicitly
  (`NotOverridingInvocationDecorator` skips the assignment, but the counter was already
  incremented before the skipped call). Mixing explicit and generated ids in the same script
  can leave bigger gaps than expected. Decide: skip advancing when the value won't be used
  (would need `NotOverridingInvocationDecorator` to short-circuit the whole expression, not
  just the setter), or just document it clearly?

#### Sequences
* [ ] Reset sequences on demand via an explicit script command, not just during cleanup
* [ ] Give scripts direct access to sequences
* [ ] Dedicated tests for sequence resets (beyond the per-DBMS strategy tests that exist today)

#### Cleanup & Table Order
* [ ] `disableConstraintsDuringCleanup()` option (not applicable to Oracle)

#### Scripts & API
* [ ] `updateDB` - update specific columns on a known row id, not just insert/reset
* [ ] Parametrized scripts?
* [ ] Split a schema across multiple interfaces
* [ ] Single-table inserts (useful for inline updates)
* [ ] Cache parsed scripts inside the engine (with an option to disable)
* [ ] `JdbsUtils`: `today(+-nDays)` (use time units?), `midnight(+-nDays)`
* [ ] add a callback for db modification? (e.g. for app's cache reset)

#### Ecosystem Integration
* [ ] Generate schema interfaces from an existing DB
* [ ] Kotlin support? (should already work, but may be improvable)
* [ ] Easy Spring integration - `examples/08-springboot` already proves a plain `DataSource` bean
  is enough with zero extra code; re-scope this if something beyond that is actually wanted
  (e.g. an autoconfiguration starter)
* [ ] Future DBMS ideas:
  * [ ] Snowflake?
  * [ ] Google BigQuery?

#### Naming, Design Questions & Housekeeping
* [ ] What exceptions should be thrown, as a general policy? (open design question)
* [ ] Ensure tests pass with `autocommit=true|false`

## Shipped
Grouped summary of completed work - see git history for detail.

* **Metadata caching**: `INSTANCE`/`GLOBAL`/`NONE` strategies, `DataSourceCacheKey` tests
* **Schema validation**: unmapped-table strategy (`LOG_WARN`/`LOG_ERROR`/`FAIL`), default
  migration-table suppression
* **Table order**: FK auto-detection with caching and a cyclic-dependency error; manual override
  via `Builder.tableDependencyOrder(...)`
* **Type conversion**: `Date`/`Instant`, enums (name/ordinal), default-method-based conversion,
  custom converters (`.converter(...)` / `.disableDefaultConverters()`)
* **Assertions**: `assertDBHas` / `assertDBHasNot`
* **Sequences**: reset-to-10000+ on cleanup, per-DBMS reset strategies, DB2 identity-owned-sequence
  handling via `JDBFeature` / `.feature(...)`; forward-only/no-rewind resets for Postgres, DB2,
  CockroachDB (a second insert into the same auto-increment table within one test no longer
  collides); Postgres hidden-IDENTITY-sequence detection fixed (`pg_class`, not
  `information_schema.sequences`, which silently excludes owned sequences)
* **Scripts**: class-based and abstract-class scripts, `include(...)` composition (from both class
  and lambda scripts), constructor-parameter guard on class scripts, reusable prepared statements
  per table
* **ID generation**: `RecordTools.nextIntId`/`nextLongId`, templated string values
  (`RecordTools.strValue`)
* **DBMS support**: MySQL, MariaDB, Postgres, Oracle, MSSQL, H2, HSQLDB, SQLite, DuckDB, DB2,
  CockroachDB (including working around its Postgres-driver detection confusing
  `PostgreSQLStrategy`), Spanner (client-generated PKs, DDL-outside-transaction handling,
  DATE/TIMESTAMP disambiguation via `ParameterMetaData`); MSSQL `IDENTITY_INSERT` now toggles
  per-record via the cache-integrated identity-column lookup, so mixed explicit/auto-generated ids
  in one script and interleaved multi-table scripts both work correctly
* **Migration testing**: `JDBMigrationEngine`/`LiquibaseMigrator`/`FlywayMigrator` all working
  (seed pre-migration shape, `migrateTo(tag)`, assert post-migration shape); auto-detects Liquibase
  vs. Flyway from the classpath (`MigrationRunnerFactory`) so `.migrator(...)` doesn't need to be
  set explicitly; Flyway modules added for every DBMS profile that needed one (Oracle, DB2,
  HSQLDB, MSSQL, Spanner); Spanner gets its own Flyway migration-script set
  (`db/flyway-migration-spanner`) since Flyway has no cross-DBMS dialect translation the way
  Liquibase does
* **Engine**: builder pattern (`JDBEngine.builder(...)`), lazy `DataSource` supplier,
  lazy engine construction, no leaked connections (tested)
* **Docs**: all public interfaces/classes documented (`JDBEngine`/`IJDBEngine`, `IDBSchema`,
  `RecordTools`/`IDBRecordTools`, `DBMSType`, `IScriptExecutor`); `skill/jdbscript/SKILL.md`
* **Infra**: TeamCity CI across all supported DBMS x JDK 17/21/25; inner non-static script
  classes throw an explaining exception; null-handling tested in the executor
* **Released**: v1.1.0 published to Maven Central (2026-09-05)

## Requirements
* JDK 17+ (uses `InvocationHandler.invokeDefault`, which needs Java 16+; project targets 17)
* [x] Deploy to Maven Central - published

# Changelog

Notable changes per release. Full notes for each version are on [GitHub Releases](https://github.com/cyberdruid-dev/jdbscript/releases).

## [Unreleased]

### Added
- `assertDBHas` / `assertDBHasNot` support LOB columns.
- `ValidationStrategy.IGNORE`: unmapped tables are only logged at DEBUG.

### Changed
- `JDBMigrationEngine` defaults to `ValidationStrategy.IGNORE`, so tables its before/after interfaces leave out no longer log a warning.
- Generated SQL quotes table and column names, using the database's own spelling of each.

### Fixed
- Tables and columns created with a quoted mixed-case name (e.g. `"OrderItems"`, `"firstName"`) can be mapped. Names match exactly first, then case-insensitively; several case-insensitive matches fail with a descriptive error.
- PostgreSQL: `byte[]` / `InputStream` values into a `bytea` column of a mixed-case table.

## [1.3.1] - 2026-10-12

### Added
- `migrateBefore(marker)`: migrate to just before the migration under test, seed rows in the old shape, then apply that one migration.
- Liquibase markers can be a tag or a changeset (`id`, `id::author` or `path::id::author`); use the `tag:` / `changeset:` prefix when a name is ambiguous.
- Example `12-kotlin-dsl`.

### Changed
- `migrateTo` / `migrateBefore` fail if the marked migration has already been applied.
- Unknown markers fail with `JDBScriptException` before any migration runs (Liquibase and Flyway).

### Fixed
- Oracle 18c: sequence reset failed with ORA-32793 when dropped tables were still in the recycle bin.
- Oracle + Flyway: `reset()` purges the recycle bin before `clean()`, avoiding ORA-32794.

## [1.3.0] - 2026-10-01

Driven by migrating a large enterprise project (9+ years of active development, thousands of tests) from jdbscript's internal predecessor to this library.

### Added
- `updateDB(...)`: change columns of rows already in the database.
- `engine.as(...)`: domain DSLs and helper mixins built on the schema interface.
- Example `11-domain-dsl-and-helpers`.

### Changed
- Generated ID counters and `RecordTools` sequence state are scoped per `IJDBEngine` instance.

### Fixed
- PostgreSQL: type mapping and conversion for `bytea` and `oid` columns.
- Oracle, DB2: sequence reset handling; internal temporary/system tables excluded from schema reflection.

## [1.2.0] - 2026-09-29

### Added
- Migration testing with Liquibase or Flyway (`JDBMigrationEngine`): seed rows in the old schema, migrate, assert on the new one.
- Google Cloud Spanner support.
- `onDataChange` callback, e.g. to clear an application cache after `resetDB` / `insertDB` / `cleanupDB`.
- Examples: Testcontainers, Spring Boot, Liquibase/Flyway data migration.

### Changed
- Cleanup restarts sequences and identity columns at exactly 10000.

### Fixed
- SQL Server: `IDENTITY_INSERT` reset on failed inserts; mixed explicit and generated IDs in one table.
- Liquibase/Flyway: work on threads without a context classloader.
- `RecordTools.strValue`: special characters and explicit nulls.
- The metadata cache no longer serializes parallel tests.

## [1.1.0] - 2026-09-06

### Added
- Automatic foreign-key ordering for `insertDB` / `cleanupDB`, with a clear error on circular dependencies.
- `Builder.tableDependencyOrder(List<String>)` to override the order manually.
- CockroachDB and DuckDB support.
- `.feature(...)` (`JDBFeature`) for DBMS-specific edge cases, e.g. DB2 identity-owned sequences.
- `assertDBHas(...)` / `assertDBHasNot(...)`.

### Changed
- Converter API: `.converter(...)` / `.disableDefaultConverters()` replace `.converters(...)`; custom converters add to the defaults instead of replacing them.

### Fixed
- `getColumnValue()` returned `false` instead of `null` for a NULL boolean column.
- SQLite timestamps were parsed with a 12-hour clock.
- Several silent failures now raise clear errors (empty asserted column set, DB2/HSQLDB sequence discovery/reset, null-unsafe assertion SQL on PostgreSQL).
- `clearCache()` didn't invalidate table discovery; `assertDBHas` / `assertDBHasNot` skipped schema validation.

## [1.0.0] - 2026-08-27

Initial release: schemas and tables as typed Java interfaces, fluent seeding, inline and class-based scripts with `db.include(...)`, `resetDB` / `cleanupDB`, `RecordTools`, type conversion (enums, UUIDs, `java.time`, custom `IJDBTypeConverter`). Supports PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, DB2, SQLite, H2 and HSQLDB.

[Unreleased]: https://github.com/cyberdruid-dev/jdbscript/compare/v1.3.1...HEAD
[1.3.1]: https://github.com/cyberdruid-dev/jdbscript/releases/tag/v1.3.1
[1.3.0]: https://github.com/cyberdruid-dev/jdbscript/releases/tag/v1.3.0
[1.2.0]: https://github.com/cyberdruid-dev/jdbscript/releases/tag/v1.2.0
[1.1.0]: https://github.com/cyberdruid-dev/jdbscript/releases/tag/v1.1.0
[1.0.0]: https://github.com/cyberdruid-dev/jdbscript/releases/tag/v1.0.0

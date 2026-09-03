# JDBScript TODO & Roadmap

### Goals:
* Simple to use
* Robust (across different dbms & jdk)
* Explainability
  * proper logging
* Extensible(?)

### TODO:
* [x] Add metadata cache
  * [x] tests: DataSourceCacheKey - url mutations to same db should result in equal DataSourceCacheKey
  * [x] tests: various cache strategies;
* [x] Add option: NONE|WARNING|ERROR on tables are missing for an schema interface.
* [x] fix table cleanup order:
  * [ ] add user option to set the order
  * [x] autodetect FK dependencies(+caching), with error message on cyclic dependencies
  * [ ] option: add disableConstraintsDuringCleanup() (not for Oracle)
* [x] test: is order of tables in script relevant?
* [x] data type conversion(e.g. Date -> seconds, Instant -> epoch ms):
  * [x] option: allow default methods with same name to do conversion
  * [ ] option: add an annotation
* [ ] implement updating db scripts(update some columns with known row id)
* [x] implement simple assertions
* [ ] handle date/timestamp precision mismatch. (e.g. mariadb with assertDBhas() for dates)
* [ ] sequence autocorrection/manual updates
  * [x] a) Set all sequences to 10000+
    * [x] a.1) reset sequences while cleanup
    * [ ] a.2) reset with dbscript command();
  * [ ] b) give access to sequences from script.
* [x] !!Create Strategies for different DBMSs (PostgreSQL/OracleSequenceResetter....)
* [ ] Tests for sequence resets
* [ ] What exceptions to throw?
* [ ] Fix naming: Db/DB/Jdb/JDBS/....
* [x] Document public interfaces and classes.
  * [x] JDBEngine/IJDBEngine
  * [x] IDbSchema
  * [x] RecordTools/IDbRecordTools
  * [x] DbmsType
  * [x] IScriptExecutor
* [x] Test: Not leaking connections.
* [x] throw error if ClassScript's constructor has parameters.
* [x] add JDBEngine()  constructor with ()->DataSource supplier
* [ ] write a skill
* Bugs:
  * [ ] Postgres <=12 can have IDENTITY column with hidden sequence (liquibase sometime generates it)
  * [x] Cockroachdb connects though postgres driver that confuses PostgreSQLStrategy
* Features:
  * [x] Class as script
  * [x] Include Scripts
    * [x] should work from Class scripts
    * [x] should work from Consumer scripts
  * [ ] defaults
    * [ ] Should work with class scripts too.
  * [ ] defaults: templates
  * [ ] defaults: generated ID
  * [ ] Types conversions
    * [ ] Warnings if DB datatype has less precision and we set data that won't match? (java Date VS db DATE)
      * [ ] Alternative: leave it to dmbs (MySQL thorws exception)
    * [x] enums: name(default),ordinal
    * [ ] custom types? - need test
    * [ ] custom converters on field(e.g. UUID can be stored in different ways)
  * [ ] JdbsUtils:
    * today(+-nDays) - use time units?
    * midnight(+-nDays)
* [ ] SQLite support:
  * [ ] SQLExecutor: how to handle date/time? (maybe don't? set it as it will be in db)
* [x] hsqldb support
* [x] db2 support(?)
* [x] Improvement: Reuse prepared statements for same tables.

### OLD TODO:
* [ ] @Default, @GeneratedId - are applied before send script to executor
  * !Problem: @Default can not accept arbitrary object as value, only exact type of primitive or a Class<?>
  * [ ] !ALTERNATIVE: defaults(Tools tools){}
    * [ ] Tools - any class that have:
      * default public constructor
      * setJDBScriptAccessor(JDBScriptAccessor) - to access script state; 
    * [ ] DefaultTools
      * [ ] getNextId(name) - implementation? (name - is table scoped (default to null? empty param?))
      * [ ] intValue(String expr) - evaluated upon values of current records
    * [ ] defaults() method is called on proxy that prevents overriding existing value.
    * [ ] tools should have some access to JDBScript in question (JDBScriptAccessor??)
* [ ] @GeneratedId - applied to db field, generates Id(=max(table.ids))
* [ ] @Default - applied to db field, if field value was not specified set the value to annotation value
* [ ] Tests:
  * [ ] check not @GeneratedId,@Default applied to same db field.
  * [ ] check @GeneratedId,@Default are not from some other package(by mistake).
  * [ ] Throw exception if some values were not specified neither in script nor in defaults.
* [ ] ensure test passes with autocommit=true|false
* [ ] Explore table name case sensitivity
* [x] Make tests pass on Oracle db
* [x] make logging work
* [x] make script class with private constructor work
* [x] run tests with different DBMS
  * [x] MySQL
  * [x] MariaDB
  * [x] Postgres
  * [x] Oracle
  * [x] MSSQl
  *  [x] configure test in TeamCity to run against multiple dbms
* [x] test for null in executor class
* [x] Setup TeamCity Builds
* [x] Handle inner nonstatic classes(throw explaining exception?)

### Requirements:
* Minimal JDK version?
  * Using InvocationHandler.invokeDefaults() -> Java16
* [Deploy to Maven Central](https://maven.apache.org/repository/guide-central-repository-upload.html)

### Possible Features:
* [ ] defaults + templating
* [ ] id autogeneration(@GeneratedId)
* [ ] sequence autocorrection/manual updates
* [ ] support different DBMS:
  * [ ] MySQL/MariaDB
  * [ ] Postgres
  * [ ] Oracle
  * [ ] H2
  * [ ] CockroachDB?
  * [ ] Snowflake?
  * [ ] IBM DB2?
  * [ ] SQLite?
  * [ ] Google BigQuery?
* [ ] autodetect table dependency -> cleanup order
* [ ] Parametrized scripts?
* [ ] Java->DB types conversions 
* [ ] possibility to split the schema into multiple interfaces
* [ ] Easy Spring integration
* [ ] Single table inserts (maybe helpful in inline updates of practical use)
* [ ] Generate Schema interfaces from DB
* [ ] engine.verifySchema() - to check that java and db schema are compatible.
* [ ] Caching scripts inside engine(with option to disable)
* [ ] Kotlin support?(should work, but maybe can be done better)
* [x] abstract classes as scripts

package org.jdbscript.utils;

import javax.sql.DataSource;

/**
 * Sets up the test database schema on a freshly created {@link DataSource}.
 * <p>
 * Most DBMS just run the shared Liquibase changelog ({@link DefaultSchemaInitStrategy}), but a
 * few need DBMS-specific handling — either because Liquibase can't be used at all (DuckDB), or
 * because Liquibase's generic type mapping produces a schema the DBMS can't actually use
 * (CockroachDB). Implementations keep those quirks isolated from {@link DataSourceFactory}.
 */
interface ITestSchemaInitStrategy {
    void initSchema(DataSource dataSource);
}

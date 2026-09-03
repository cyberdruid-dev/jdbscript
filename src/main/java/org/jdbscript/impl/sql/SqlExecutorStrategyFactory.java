package org.jdbscript.impl.sql;

import org.jdbscript.DbmsType;

public class SqlExecutorStrategyFactory {
    public static ISqlExecutorStrategy getStrategy(DbmsType dbmsType) {
        if (dbmsType == null) {
            return new DefaultSqlExecutorStrategy();
        }
        return switch (dbmsType) {
            case POSTGRESQL -> new PostgreSQLStrategy();
            case MSSQL -> new MssqlStrategy();
            case ORACLE -> new OracleStrategy();
            case HSQLDB -> new HsqldbStrategy();
            case DB2 -> new Db2Strategy();
            case COCKROACHDB -> new CockroachDBStrategy();
            case DUCKDB -> new DuckdbStrategy();
            case SQLITE -> new SqliteStrategy();
            default -> new DefaultSqlExecutorStrategy();
        };
    }
}

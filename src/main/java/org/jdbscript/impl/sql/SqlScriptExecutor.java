package org.jdbscript.impl.sql;

import org.jdbscript.DbmsType;
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.IScriptExecutor;
import org.jdbscript.impl.JDbRecord;
import org.jdbscript.impl.JDbScript;
import org.jdbscript.impl.TypedNull;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.NoCache;
import org.jdbscript.impl.sql.SqlConnectionProvider.JdbcConnectionConsumer;
import org.jdbscript.impl.sql.SqlConnectionProvider.JdbcSessionConsumer;
import org.jdbscript.impl.sql.SqlConnectionProvider.PreparedStatementProvider;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.jdbscript.DbmsType.UNKNOWN;
import static org.jdbscript.errors.Checks.checkIsNull;
import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JdbsErrors.DATASOURCE_ALREADY_SET;
import static org.jdbscript.errors.JdbsErrors.DATASOURCE_IS_NOT_CONFIGURED;

public class SqlScriptExecutor implements IScriptExecutor {
    private static final Logger log = LoggerFactory.getLogger(SqlScriptExecutor.class);
 
    private SqlConnectionProvider connectionProvider;
    private ISqlExecutorStrategy strategy;
    private SqlMetadataProvider metadataProvider;
    private IJDBCache cache = new NoCache();
    private final Map<String, String> insertSqlCache = new ConcurrentHashMap<>();
    private final Map<String, String> selectSqlCache = new ConcurrentHashMap<>();
 
    public SqlScriptExecutor() {
    }

    public void setDbmsType(DbmsType dbmsType) {
        this.strategy = switch (dbmsType) {
            case MSSQL -> new MssqlStrategy();
            case HSQLDB -> new HsqldbStrategy();
            case ORACLE -> new OracleStrategy();
            case POSTGRESQL -> new PostgreSQLStrategy();
            case COCKROACHDB -> new CockroachDBStrategy();
            case DB2 -> new Db2Strategy();
            default -> new DefaultSqlExecutorStrategy();
        };
    }

    private ISqlExecutorStrategy getStrategy() {
        if (strategy == null) {
            DbmsType dbmsType = getMetadataProvider() != null ? getMetadataProvider().getDbmsType() : UNKNOWN;
            setDbmsType(dbmsType);
        }
        return strategy;
    }

    @Override
    public void setDataSource(DataSource value) {
        checkIsNull(this.connectionProvider, DATASOURCE_ALREADY_SET);
        this.connectionProvider = new SqlConnectionProvider(value);
        this.metadataProvider = new SqlMetadataProvider(connectionProvider);
        this.metadataProvider.setCache(this.cache);
    }

    @Override
    public void insert(JDbScript dbScript) {
        withPreparedStatements((cnn, stmtProvider) -> {
            getStrategy().beforeInsert(cnn, dbScript);
            for (var record : dbScript.getRecords()) {
                List<String> columns = getSortedColumns(record);
                String sqlKey = record.getTableName() + ":" + String.join(",", columns);
                String sql = insertSqlCache.computeIfAbsent(sqlKey, k -> createInsertSql(record, columns));
                PreparedStatement stmt = stmtProvider.get(sql);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = record.getColumns().get(columns.get(i));
                    setColumnValue(stmt, i + 1, value);
                }
                stmt.execute();
            }
            getStrategy().afterInsert(cnn);
        });
    }

    private void withConnection(JdbcConnectionConsumer consumer) {
        checkNotNull(connectionProvider, DATASOURCE_IS_NOT_CONFIGURED);
        connectionProvider.withConnection(consumer);
    }

    private void withPreparedStatements(JdbcSessionConsumer consumer) {
        checkNotNull(connectionProvider, DATASOURCE_IS_NOT_CONFIGURED);
        connectionProvider.withPreparedStatements(consumer);
    }

    private void setColumnValue(PreparedStatement stmt, int columnIndex, Object value) throws SQLException {
        Class<?> argumentType = detectValueType(value);
        value = (value instanceof TypedNull)? null : value;
        if (InputStream.class.isAssignableFrom(argumentType)) {
            getStrategy().setInputStream(stmt, columnIndex, (InputStream) value);
        } else if(byte[].class.isAssignableFrom(argumentType)) {
            getStrategy().setByteArray(stmt, columnIndex, (byte[])value);
        } else if (UUID.class.isAssignableFrom(argumentType)) {
            getStrategy().setUUID(stmt, columnIndex, (UUID)value);
        } else {
            getStrategy().setObject(stmt, columnIndex, value);
        }
    }
//
    private Class<?> detectValueType(Object value) {
        if(value == null) {
            return Object.class;
        } else if(value instanceof TypedNull){
            return ((TypedNull)value).getType();
        }else {
            return value.getClass();
        }
    }

    @Override
    public void cleanupTables(List<String> tableNames) {
        withConnection((cnn)->{
            try(Statement stmt = cnn.createStatement()) {
                for (var tableName : tableNames) {
                    String sql = createDeleteAllSql(tableName);
                    stmt.execute(sql);
                }
            }
        });
    }

    @Override
    public IMetadataProvider getMetadataProvider() {
        return metadataProvider;
    }

    @Override
    public void assertRowsExist(JDbScript script) {
        withPreparedStatements((cnn, stmtProvider) -> {
            for (JDbRecord record : script.getRecords()) {
                List<String> columns = getSortedColumns(record);
                String sqlKey = record.getTableName() + ":" + String.join(",", columns);
                String sql = selectSqlCache.computeIfAbsent(sqlKey, k -> createSelectAssertSql(record, columns));
                PreparedStatement stmt = stmtProvider.get(sql);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = record.getColumns().get(columns.get(i));
                    setColumnValue(stmt, i + 1, value);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Fail to count rows in DB. Unexpectedly empty resultset.");
                    }
                    long count = rs.getLong(1);
                    if (count == 0) {
                        throw new AssertionFailedError("Expected row to exist.");
                    }
                }
            }
        });

    }

    @Override
    public void assertRowsNotExist(JDbScript script) {
        withPreparedStatements((cnn, stmtProvider) -> {
            for (JDbRecord record : script.getRecords()) {
                List<String> columns = getSortedColumns(record);
                String sqlKey = record.getTableName() + ":" + String.join(",", columns);
                String sql = selectSqlCache.computeIfAbsent(sqlKey, k -> createSelectAssertSql(record, columns));
                PreparedStatement stmt = stmtProvider.get(sql);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = record.getColumns().get(columns.get(i));
                    setColumnValue(stmt, i + 1, value);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Fail to count rows in DB. Unexpectedly empty resultset.");
                    }
                    long count = rs.getLong(1);
                    if (count > 0) {
                        throw new AssertionFailedError("Expected row to NOT exist.");
                    }
                }
            }
        });
    }

    @Override
    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
        if (this.metadataProvider != null) {
            this.metadataProvider.setCache(this.cache);
        }
    }

    private List<String> getSortedColumns(JDbRecord record) {
        List<String> columns = new ArrayList<>(record.getColumns().keySet());
        Collections.sort(columns);
        return columns;
    }

    private String createSelectAssertSql(JDbRecord record, List<String> columns) {
        String sql = "SELECT count(*) FROM " + record.getTableName();
        sql += " WHERE "+columns.get(0)+" = ?";
        for(int i= 1; i< columns.size(); i++){
            sql +=" AND "+columns.get(i)+" = ?";
        }
        return sql;
    }

    private String createInsertSql(JDbRecord record, List<String> columns) {
        String sql = "INSERT INTO " + record.getTableName();
        sql += " ( "+ String.join(",", columns) + " )";
        sql += " VALUES ";
        String params = ",?".repeat(columns.size()).substring(1);
        sql += " ( "+ params + " )";
        return sql;
    }

    private String createDeleteAllSql(String tableName) {
        String sql = "DELETE FROM " + tableName;
        return sql;
    }


}

package org.jdbscript.impl.sql;

import org.jdbscript.DbmsType;
import org.jdbscript.IScriptExecutor;
import org.jdbscript.impl.JDbRecord;
import org.jdbscript.impl.JDbScript;
import org.jdbscript.impl.TypedNull;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.NoCache;
import org.jdbscript.impl.sql.SqlConnectionProvider.JdbcConnectionConsumer;
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
import java.util.List;
import java.util.UUID;

import static org.jdbscript.DbmsType.UNKNOWN;
import static org.jdbscript.errors.Checks.checkIsNull;
import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JdbsErrors.DATASOURCE_ALREADY_SET;
import static org.jdbscript.errors.JdbsErrors.DATASOURCE_IS_NOT_CONFIGURED;

public class SqlScriptExecutor implements IScriptExecutor {
    private static final Logger log = LoggerFactory.getLogger(SqlScriptExecutor.class);

    private SqlConnectionProvider connectionProvider;
    private ISqlExecutorStrategy strategy;
    private MetadataTableSorter tableSorter;
    private IJDBCache cache = new NoCache();

    public SqlScriptExecutor() {
        setDbmsType(UNKNOWN);
    }

    @Override
    public void setDbmsType(DbmsType dbmsType) {
        strategy = switch (dbmsType) {
            case MSSQL -> new MssqlStrategy();
            case HSQLDB -> new HsqldbStrategy();
            case ORACLE -> new OracleStrategy();
            case POSTGRESQL -> new PostgreSQLStrategy();
            case DB2 -> new Db2Strategy();
            default -> new DefaultSqlExecutorStrategy();
        };
    }

    @Override
    public void setDataSource(DataSource value) {
        checkIsNull(this.connectionProvider, DATASOURCE_ALREADY_SET);
        this.connectionProvider = new SqlConnectionProvider(value);
        this.tableSorter = new MetadataTableSorter(connectionProvider);
        this.tableSorter.setCache(this.cache);
    }

    @Override
    public void insert(JDbScript dbScript) {
        withConnection((cnn)-> {
            strategy.beforeInsert(cnn, dbScript);
            for (var record : dbScript.getRecords()) {
                List<String> columns = new ArrayList<>(record.getColumns().keySet());
                String sql = createInsertSql(record, columns);
                try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = record.getColumns().get(columns.get(i));
                        setColumnValue(stmt, i+1, value);
                    }
                    stmt.execute();
                }
            }
            cnn.commit();
            strategy.afterInsert(cnn);
        });
    }

    private void withConnection(JdbcConnectionConsumer consumer) {
        checkNotNull(connectionProvider, DATASOURCE_IS_NOT_CONFIGURED);
        connectionProvider.withConnection(consumer);
    }

    private void setColumnValue(PreparedStatement stmt, int columnIndex, Object value) throws SQLException {
        Class<?> argumentType = detectValueType(value);
        value = (value instanceof TypedNull)? null : value;
        if (InputStream.class.isAssignableFrom(argumentType)) {
            strategy.setInputStream(stmt, columnIndex, (InputStream) value);
        } else if(byte[].class.isAssignableFrom(argumentType)) {
            strategy.setByteArray(stmt, columnIndex, (byte[])value);
        } else if (UUID.class.isAssignableFrom(argumentType)) {
            strategy.setUUID(stmt, columnIndex, (UUID)value);
        } else {
            strategy.setObject(stmt, columnIndex, value);
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
    public List<String> sortTablesByDependencies(List<String> tableNames) {
        return tableSorter.sortTablesByDependencies(tableNames);
    }

    @Override
    public void assertRowsExist(JDbScript script) {
        withConnection((cnn)->{
            for(JDbRecord record: script.getRecords()){
                List<String> columns = new ArrayList<>(record.getColumns().keySet());
                String sql = createSelectAssertSql(record, columns);
                try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = record.getColumns().get(columns.get(i));
                        setColumnValue(stmt, i+1, value);
                    }
                    try(ResultSet rs = stmt.executeQuery()){
                        if(!rs.next()) {
                            throw new RuntimeException("Fail to count rows in DB. Unexpectedly empty resultset.");
                        }
                        long count = rs.getLong(1);
                        if(count == 0 ) {
                            throw new AssertionFailedError("Expected row to exist.");
                        }
                    }
                }
            }
        });

    }

    @Override
    public void assertRowsNotExist(JDbScript script) {
        withConnection((cnn)->{
            for(JDbRecord record: script.getRecords()){
                List<String> columns = new ArrayList<>(record.getColumns().keySet());
                String sql = createSelectAssertSql(record, columns);
                try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = record.getColumns().get(columns.get(i));
                        setColumnValue(stmt, i+1, value);
                    }
                    try(ResultSet rs = stmt.executeQuery()){
                        if(!rs.next()) {
                            throw new RuntimeException("Fail to count rows in DB. Unexpectedly empty resultset.");
                        }
                        long count = rs.getLong(1);
                        if(count > 0 ) {
                            throw new AssertionFailedError("Expected row to NOT exist.");
                        }
                    }
                }
            }
        });
    }

    @Override
    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
        if (this.tableSorter != null) {
            this.tableSorter.setCache(this.cache);
        }
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

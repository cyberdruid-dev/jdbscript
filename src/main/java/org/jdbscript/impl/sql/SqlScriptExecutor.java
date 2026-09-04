package org.jdbscript.impl.sql;

import org.jdbscript.DBMSType;
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.IScriptExecutor;
import org.jdbscript.impl.JDBRecord;
import org.jdbscript.impl.JDBScript;
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

import static org.jdbscript.DBMSType.UNKNOWN;
import static org.jdbscript.errors.Checks.checkIsNull;
import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JDBErrors.DATASOURCE_ALREADY_SET;
import static org.jdbscript.errors.JDBErrors.DATASOURCE_IS_NOT_CONFIGURED;
import static org.jdbscript.errors.JDBErrors.EMPTY_ASSERTION_RECORD;

public class SqlScriptExecutor implements IScriptExecutor {
    private static final Logger log = LoggerFactory.getLogger(SqlScriptExecutor.class);
 
    private SqlConnectionProvider connectionProvider;
    private ISqlExecutorStrategy strategy;
    private SqlMetadataProvider metadataProvider;
    private IJDBCache cache = new NoCache();
    private final Map<String, String> insertSqlCache = new ConcurrentHashMap<>();
 
    public SqlScriptExecutor() {
    }

    public void setDbmsType(DBMSType dbmsType) {
        this.strategy = SqlExecutorStrategyFactory.getStrategy(dbmsType);
        if (this.connectionProvider != null) {
            this.connectionProvider.setStrategy(this.strategy);
        }
        if (this.metadataProvider != null) {
            this.metadataProvider.setStrategy(this.strategy);
        }
    }

    private ISqlExecutorStrategy getStrategy() {
        if (strategy == null) {
            DBMSType dbmsType = getMetadataProvider() != null ? getMetadataProvider().getDbmsType() : UNKNOWN;
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
        if (this.strategy != null) {
            this.connectionProvider.setStrategy(this.strategy);
            this.metadataProvider.setStrategy(this.strategy);
        }
    }

    @Override
    public void insert(JDBScript dbScript) {
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
                    log.debug("Executing cleanup SQL: {}", sql);
                    int deleted = stmt.executeUpdate(sql);
                    log.debug("Deleted {} rows from {}", deleted, tableName);
                }
            }
        });
    }

    @Override
    public IMetadataProvider getMetadataProvider() {
        return metadataProvider;
    }

    @Override
    public void assertRowsExist(JDBScript script) {
        withPreparedStatements((cnn, stmtProvider) -> {
            for (JDBRecord record : script.getRecords()) {
                if (countMatchingRows(record, stmtProvider) == 0) {
                    throw new AssertionFailedError("Expected row to exist.");
                }
            }
        });
    }

    @Override
    public void assertRowsNotExist(JDBScript script) {
        withPreparedStatements((cnn, stmtProvider) -> {
            for (JDBRecord record : script.getRecords()) {
                if (countMatchingRows(record, stmtProvider) > 0) {
                    throw new AssertionFailedError("Expected row to NOT exist.");
                }
            }
        });
    }

    private long countMatchingRows(JDBRecord record, PreparedStatementProvider stmtProvider) throws SQLException {
        // Not cached: the SQL shape now depends on which columns are null for this specific record
        // (see createSelectAssertSql), and building it is cheap string concatenation, not a JDBC
        // round trip - the thing actually worth caching, the PreparedStatement, is already handled
        // by stmtProvider.get(sql), keyed on this exact SQL text.
        List<String> columns = getSortedColumns(record);
        if (columns.isEmpty()) {
            throw EMPTY_ASSERTION_RECORD.get(record.getTableName());
        }
        String sql = createSelectAssertSql(record, columns);
        PreparedStatement stmt = stmtProvider.get(sql);
        int paramIndex = 1;
        for (String column : columns) {
            Object value = record.getColumns().get(column);
            if (!isNullValue(value)) {
                setColumnValue(stmt, paramIndex++, value);
            }
            // A null column is expressed as a literal "IS NULL" in the SQL - nothing to bind.
        }
        try (ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                throw new RuntimeException("Fail to count rows in DB. Unexpectedly empty resultset.");
            }
            return rs.getLong(1);
        }
    }

    @Override
    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
        if (this.metadataProvider != null) {
            this.metadataProvider.setCache(this.cache);
        }
    }

    private List<String> getSortedColumns(JDBRecord record) {
        List<String> columns = new ArrayList<>(record.getColumns().keySet());
        Collections.sort(columns);
        return columns;
    }

    /**
     * {@code col = ?} never matches a bound NULL (SQL's {@code = NULL} is UNKNOWN, not TRUE), so an
     * asserted record with a null column would otherwise never be found. Building this per-record,
     * aware of which columns are actually null, avoids that without needing a NULL-safe operator:
     * a null column becomes a literal {@code col IS NULL} (no parameter), a non-null one stays a
     * plain, fully-typed {@code col = ?}. (An earlier version bound the same value twice via
     * {@code (col = ? OR (? IS NULL AND col IS NULL))} - portable everywhere we could test offline,
     * but PostgreSQL's extended query protocol can't infer a type for the bare {@code ? IS NULL}
     * parameter, since nothing else pins it to a concrete type, and rejects it at prepare time.)
     */
    private String createSelectAssertSql(JDBRecord record, List<String> columns) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM ").append(record.getTableName()).append(" WHERE ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            String column = columns.get(i);
            Object value = record.getColumns().get(column);
            sql.append(column).append(isNullValue(value) ? " IS NULL" : " = ?");
        }
        return sql.toString();
    }

    private boolean isNullValue(Object value) {
        return value == null || value instanceof TypedNull;
    }

    private String createInsertSql(JDBRecord record, List<String> columns) {
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

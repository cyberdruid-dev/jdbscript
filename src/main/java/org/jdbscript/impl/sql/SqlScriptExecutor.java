package org.jdbscript.impl.sql;

import org.jdbscript.DBMSType;
import org.jdbscript.JDBFeatureSet;
import org.jdbscript.impl.CachingMetadataProvider;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.jdbscript.DBMSType.UNKNOWN;
import static org.jdbscript.errors.Checks.checkIsNull;
import static java.util.stream.Collectors.joining;
import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JDBErrors.ASSERTION_UNSUPPORTED_LOB_VALUE;
import static org.jdbscript.errors.JDBErrors.DATASOURCE_ALREADY_SET;
import static org.jdbscript.errors.JDBErrors.DATASOURCE_IS_NOT_CONFIGURED;
import static org.jdbscript.errors.JDBErrors.EMPTY_ASSERTION_RECORD;
import static org.jdbscript.errors.JDBErrors.UPDATE_NOTHING_TO_SET;
import static org.jdbscript.errors.JDBErrors.UPDATE_PRIMARY_KEY_NOT_SET;
import static org.jdbscript.errors.JDBErrors.UPDATE_ROW_NOT_FOUND;
import static org.jdbscript.errors.JDBErrors.UPDATE_TABLE_HAS_NO_PRIMARY_KEY;

public class SqlScriptExecutor implements IScriptExecutor {
    private static final Logger log = LoggerFactory.getLogger(SqlScriptExecutor.class);
 
    private SqlConnectionProvider connectionProvider;
    private volatile ISqlExecutorStrategy strategy;
    private SqlMetadataProvider sqlMetadataProvider;
    private CachingMetadataProvider metadataProvider;
    private IJDBCache cache = new NoCache();
    private JDBFeatureSet features = JDBFeatureSet.empty();
    private final Map<String, String> insertSqlCache = new ConcurrentHashMap<>();

    public SqlScriptExecutor() {
    }

    public synchronized void setDbmsType(DBMSType dbmsType) {
        // Assigned last: getStrategy() reads it without locking.
        ISqlExecutorStrategy newStrategy = SqlExecutorStrategyFactory.getStrategy(dbmsType);
        newStrategy.setFeatures(this.features);
        newStrategy.setMetadataProvider(this.metadataProvider);
        if (this.connectionProvider != null) {
            this.connectionProvider.setStrategy(newStrategy);
        }
        if (this.sqlMetadataProvider != null) {
            this.sqlMetadataProvider.setStrategy(newStrategy);
        }
        this.strategy = newStrategy;
    }

    @Override
    public synchronized void setFeatures(JDBFeatureSet features) {
        this.features = features != null ? features : JDBFeatureSet.empty();
        if (this.strategy != null) {
            this.strategy.setFeatures(this.features);
        }
    }

    private ISqlExecutorStrategy getStrategy() {
        ISqlExecutorStrategy current = strategy;
        if (current != null) {
            return current;
        }
        DBMSType dbmsType = detectDbmsType();
        synchronized (this) {
            if (strategy == null) {
                setDbmsType(dbmsType);
            }
            return strategy;
        }
    }

    private DBMSType detectDbmsType() {
        return getMetadataProvider() != null ? getMetadataProvider().getDbmsType() : UNKNOWN;
    }

    @Override
    public synchronized void setDataSource(DataSource value) {
        checkIsNull(this.connectionProvider, DATASOURCE_ALREADY_SET);
        this.connectionProvider = new SqlConnectionProvider(value);
        this.sqlMetadataProvider = new SqlMetadataProvider(connectionProvider);
        this.metadataProvider = new CachingMetadataProvider(sqlMetadataProvider);
        this.metadataProvider.setCache(this.cache);
        if (this.strategy != null) {
            this.connectionProvider.setStrategy(this.strategy);
            this.sqlMetadataProvider.setStrategy(this.strategy);
            this.strategy.setMetadataProvider(this.metadataProvider);
        }
    }

    @Override
    public void insert(JDBScript dbScript) {
        withPreparedStatements((cnn, stmtProvider) -> {
            getStrategy().beforeInsert(cnn, dbScript);
            try {
                for (var record : dbScript.getRecords()) {
                    getStrategy().beforeEachInsert(cnn, record);
                    List<String> columns = getSortedColumns(record);
                    String sqlKey = record.getTableName() + ":" + String.join(",", columns);
                    String sql = insertSqlCache.computeIfAbsent(sqlKey, k -> createInsertSql(record, columns));
                    PreparedStatement stmt = stmtProvider.get(sql);
                    for (int i = 0; i < columns.size(); i++) {
                        String column = columns.get(i);
                        setColumnValue(cnn, record.getTableName(), column, stmt, i + 1, record.getColumns().get(column));
                    }
                    stmt.execute();
                }
            } catch (Throwable e) {
                // Must run even on failure: MSSQL's IDENTITY_INSERT would otherwise stay ON for the pooled connection.
                try {
                    getStrategy().afterInsert(cnn);
                } catch (Throwable cleanupError) {
                    e.addSuppressed(cleanupError);
                }
                throw e;
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

    private void setColumnValue(Connection cnn, String tableName, String columnName, PreparedStatement stmt, int columnIndex, Object value) throws SQLException {
        Class<?> argumentType = detectValueType(value);
        value = (value instanceof TypedNull)? null : value;
        if (InputStream.class.isAssignableFrom(argumentType)) {
            getStrategy().setInputStream(cnn, metadataProvider.toDbTableName(tableName), columnName, stmt, columnIndex, (InputStream) value);
        } else if(byte[].class.isAssignableFrom(argumentType)) {
            getStrategy().setByteArray(cnn, metadataProvider.toDbTableName(tableName), columnName, stmt, columnIndex, (byte[])value);
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
            getStrategy().resetSequences(cnn, tableNames);
        });
    }

    @Override
    public IMetadataProvider getMetadataProvider() {
        return metadataProvider;
    }

    @Override
    public void update(JDBScript dbScript) {
        withPreparedStatements((cnn, stmtProvider) -> {
            for (JDBRecord record : dbScript.getRecords()) {
                updateRecord(cnn, record, stmtProvider);
            }
        });
    }

    private void updateRecord(Connection cnn, JDBRecord record, PreparedStatementProvider stmtProvider) throws SQLException {
        List<String> primaryKey = metadataProvider.getPrimaryKeyColumns(metadataProvider.toDbTableName(record.getTableName()));
        if (primaryKey.isEmpty()) {
            throw UPDATE_TABLE_HAS_NO_PRIMARY_KEY.get(record.getTableName());
        }
        List<String> keyColumns = new ArrayList<>();
        List<String> setColumns = new ArrayList<>();
        for (String column : getSortedColumns(record)) {
            boolean isKey = primaryKey.stream().anyMatch(pk -> pk.equalsIgnoreCase(column));
            (isKey ? keyColumns : setColumns).add(column);
        }
        List<String> missingKeys = primaryKey.stream()
                .filter(pk -> keyColumns.stream().noneMatch(pk::equalsIgnoreCase))
                .toList();
        if (!missingKeys.isEmpty()) {
            throw UPDATE_PRIMARY_KEY_NOT_SET.get(record.getTableName(), String.join(", ", missingKeys));
        }
        if (setColumns.isEmpty()) {
            throw UPDATE_NOTHING_TO_SET.get(record.getTableName());
        }
        String sql = "UPDATE " + metadataProvider.quoteTableName(record.getTableName())
                + " SET " + setColumns.stream().map(c -> quotedColumn(record, c) + " = ?").collect(joining(", "))
                + " WHERE " + keyColumns.stream().map(c -> quotedColumn(record, c) + " = ?").collect(joining(" AND "));
        PreparedStatement stmt = stmtProvider.get(sql);
        int paramIndex = 1;
        for (String column : setColumns) {
            setColumnValue(cnn, record.getTableName(), column, stmt, paramIndex++, record.getColumns().get(column));
        }
        for (String column : keyColumns) {
            setColumnValue(cnn, record.getTableName(), column, stmt, paramIndex++, record.getColumns().get(column));
        }
        if (stmt.executeUpdate() == 0) {
            String key = keyColumns.stream().map(c -> c + "=" + record.getColumns().get(c)).collect(joining(", "));
            throw UPDATE_ROW_NOT_FOUND.get(record.getTableName(), key);
        }
    }

    @Override
    public void assertRowsExist(JDBScript script) {
        withPreparedStatements((cnn, stmtProvider) -> {
            for (JDBRecord record : script.getRecords()) {
                if (countMatchingRows(cnn, record, stmtProvider) == 0) {
                    throw new AssertionFailedError("Expected row to exist.");
                }
            }
        });
    }

    @Override
    public void assertRowsNotExist(JDBScript script) {
        withPreparedStatements((cnn, stmtProvider) -> {
            for (JDBRecord record : script.getRecords()) {
                if (countMatchingRows(cnn, record, stmtProvider) > 0) {
                    throw new AssertionFailedError("Expected row to NOT exist.");
                }
            }
        });
    }

    private long countMatchingRows(Connection cnn, JDBRecord record, PreparedStatementProvider stmtProvider) throws SQLException {
        // Not cached: the SQL shape now depends on which columns are null for this specific record
        // (see createSelectAssertSql), and building it is cheap string concatenation, not a JDBC
        // round trip - the thing actually worth caching, the PreparedStatement, is already handled
        // by stmtProvider.get(sql), keyed on this exact SQL text.
        List<String> columns = getSortedColumns(record);
        if (columns.isEmpty()) {
            throw EMPTY_ASSERTION_RECORD.get(record.getTableName());
        }
        // Some DBMS (e.g. Oracle) can't compare LOBs with "=", so non-null LOB values are fetched
        // and compared here instead of in the WHERE clause.
        Map<String, Integer> lobColumns = metadataProvider.getLobColumns(metadataProvider.toDbTableName(record.getTableName()));
        List<String> javaCompared = new ArrayList<>();
        List<String> sqlCompared = new ArrayList<>();
        for (String column : columns) {
            boolean comparedInJava = lobColumns.containsKey(column.toUpperCase()) && !isNullValue(record.getColumns().get(column));
            (comparedInJava ? javaCompared : sqlCompared).add(column);
        }
        List<Object> expectedLobs = new ArrayList<>();
        for (String column : javaCompared) {
            int lobType = lobColumns.get(column.toUpperCase());
            expectedLobs.add(toComparableLob(record.getTableName(), column, lobType, record.getColumns().get(column)));
        }
        String sql = createSelectAssertSql(record, sqlCompared, javaCompared);
        PreparedStatement stmt = stmtProvider.get(sql);
        int paramIndex = 1;
        for (String column : sqlCompared) {
            Object value = record.getColumns().get(column);
            if (!isNullValue(value)) {
                setColumnValue(cnn, record.getTableName(), column, stmt, paramIndex++, value);
            }
            // A null column is expressed as a literal "IS NULL" in the SQL - nothing to bind.
        }
        try (ResultSet rs = stmt.executeQuery()) {
            return expectedLobs.isEmpty() ? readCount(rs) : countLobMatches(rs, expectedLobs);
        }
    }

    private long readCount(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            throw new RuntimeException("Fail to count rows in DB. Unexpectedly empty resultset.");
        }
        return rs.getLong(1);
    }

    private long countLobMatches(ResultSet rs, List<Object> expectedLobs) throws SQLException {
        long count = 0;
        while (rs.next()) {
            if (lobsMatch(rs, expectedLobs)) {
                count++;
            }
        }
        return count;
    }

    // An InputStream can be read only once, but is compared against every candidate row.
    private Object toComparableLob(String tableName, String column, int lobType, Object value) {
        boolean supported = lobType == Types.BLOB
                ? value instanceof byte[] || value instanceof InputStream
                : value instanceof String;
        if (!supported) {
            throw ASSERTION_UNSUPPORTED_LOB_VALUE.get(tableName, column, value.getClass().getSimpleName());
        }
        if (value instanceof InputStream) {
            try {
                return ((InputStream) value).readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return value;
    }

    private boolean lobsMatch(ResultSet rs, List<Object> expectedLobs) throws SQLException {
        for (int i = 0; i < expectedLobs.size(); i++) {
            Object expected = expectedLobs.get(i);
            boolean matches = expected instanceof byte[]
                    ? Arrays.equals((byte[]) expected, rs.getBytes(i + 1))
                    : expected.equals(rs.getString(i + 1));
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    @Override
    public synchronized void setCache(IJDBCache cache) {
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
    private String createSelectAssertSql(JDBRecord record, List<String> columns, List<String> selectedColumns) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(selectedColumns.isEmpty() ? "count(*)" : selectedColumns.stream()
                        .map(c -> quotedColumn(record, c))
                        .collect(joining(", ")))
                .append(" FROM ").append(metadataProvider.quoteTableName(record.getTableName()));
        for (int i = 0; i < columns.size(); i++) {
            sql.append(i == 0 ? " WHERE " : " AND ");
            String column = columns.get(i);
            Object value = record.getColumns().get(column);
            sql.append(quotedColumn(record, column)).append(isNullValue(value) ? " IS NULL" : " = ?");
        }
        return sql.toString();
    }

    private boolean isNullValue(Object value) {
        return value == null || value instanceof TypedNull || ("".equals(value) && getStrategy().isEmptyStringNull());
    }

    private String quotedColumn(JDBRecord record, String column) {
        return metadataProvider.quoteColumnName(record.getTableName(), column);
    }

    private String createInsertSql(JDBRecord record, List<String> columns) {
        String sql = "INSERT INTO " + metadataProvider.quoteTableName(record.getTableName());
        sql += " ( " + columns.stream().map(c -> quotedColumn(record, c)).collect(joining(",")) + " )";
        sql += " VALUES ";
        String params = ",?".repeat(columns.size()).substring(1);
        sql += " ( "+ params + " )";
        return sql;
    }

    private String createDeleteAllSql(String tableName) {
        String sql = "DELETE FROM " + metadataProvider.quoteTableName(tableName) + " WHERE 1=1";
        return sql;
    }


}

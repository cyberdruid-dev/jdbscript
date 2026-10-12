package org.jdbscript.impl.sql;

import org.jdbscript.DBMSType;
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.impl.sql.SqlConnectionProvider.JdbcConnectionConsumer;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.*;

public class SqlMetadataProvider implements IMetadataProvider {

    private final SqlConnectionProvider connectionProvider;
    private volatile ISqlExecutorStrategy strategy;

    public SqlMetadataProvider(SqlConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void setStrategy(ISqlExecutorStrategy strategy) {
        this.strategy = strategy;
    }

    private ISqlExecutorStrategy getStrategy() {
        if (strategy == null) {
            DBMSType type = getDbmsType();
            this.strategy = SqlExecutorStrategyFactory.getStrategy(type);
        }
        return strategy;
    }

    @Override
    public DBMSType getDbmsType() {
        final DBMSType[] detected = new DBMSType[1];
        withConnection(cnn -> {
            detected[0] = DBMSType.getType(cnn.getMetaData());
        });
        return detected[0];
    }

    @Override
    public List<String> getAllTables() {
        List<String> tables = new ArrayList<>();
        withConnection(cnn -> {
            DatabaseMetaData metaData = cnn.getMetaData();
            String searchCatalog = getStrategy().getSearchCatalog(cnn);
            String searchSchema = getStrategy().getSearchSchema(cnn);
            String[] types = getStrategy().getTableTypes();
            Set<String> temporaryTables = getStrategy().getTemporaryTables(cnn, searchSchema);
            try (ResultSet rs = metaData.getTables(searchCatalog, searchSchema, "%", types)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!temporaryTables.contains(tableName)) {
                        tables.add(tableName);
                    }
                }
            }
        });
        return tables;
    }

    @Override
    public Set<String> getTableDependencies(String tableName) {
        Set<String> dependencies = new HashSet<>();
        withConnection(cnn -> {
            dependencies.addAll(getStrategy().getRawTableDependencies(cnn, cnn.getCatalog(), cnn.getSchema(), tableName));
        });
        return dependencies;
    }

    @Override
    public List<String> getPrimaryKeyColumns(String dbTableName) {
        List<String> columns = new ArrayList<>();
        withConnection(cnn -> {
            String searchCatalog = getStrategy().getSearchCatalog(cnn);
            String searchSchema = getStrategy().getSearchSchema(cnn);
            try (ResultSet rs = cnn.getMetaData().getPrimaryKeys(searchCatalog, searchSchema, dbTableName)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        });
        return columns;
    }

    @Override
    public List<String> getColumns(String dbTableName) {
        List<String> columns = new ArrayList<>();
        withConnection(cnn -> {
            String searchCatalog = getStrategy().getSearchCatalog(cnn);
            String searchSchema = getStrategy().getSearchSchema(cnn);
            try (ResultSet rs = cnn.getMetaData().getColumns(searchCatalog, searchSchema, dbTableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        });
        return columns;
    }

    @Override
    public Map<String, Integer> getLobColumns(String dbTableName) {
        Map<String, Integer> columns = new HashMap<>();
        withConnection(cnn -> {
            String searchCatalog = getStrategy().getSearchCatalog(cnn);
            String searchSchema = getStrategy().getSearchSchema(cnn);
            try (ResultSet rs = cnn.getMetaData().getColumns(searchCatalog, searchSchema, dbTableName, null)) {
                while (rs.next()) {
                    int type = rs.getInt("DATA_TYPE");
                    if (type == Types.CLOB || type == Types.NCLOB || type == Types.BLOB) {
                        columns.put(rs.getString("COLUMN_NAME").toUpperCase(), type);
                    }
                }
            }
        });
        return columns;
    }

    @Override
    public Map<String, String> getColumnTypes(String dbTableName) {
        Map<String, String> types = new HashMap<>();
        withConnection(cnn -> types.putAll(getStrategy().findColumnTypes(cnn, dbTableName)));
        return types;
    }

    @Override
    public Optional<String> getIdentityColumn(String dbTableName) {
        final String[] identityColumn = new String[1];
        withConnection(cnn -> {
            try (ResultSet rs = cnn.getMetaData().getColumns(null, null, dbTableName, "%")) {
                while (rs.next() && identityColumn[0] == null) {
                    if ("YES".equals(rs.getString("IS_AUTOINCREMENT"))) {
                        identityColumn[0] = rs.getString("COLUMN_NAME");
                    }
                }
            }
        });
        return Optional.ofNullable(identityColumn[0]);
    }

    @Override
    public String getIdentifierQuote() {
        final String[] quote = new String[1];
        withConnection(cnn -> quote[0] = cnn.getMetaData().getIdentifierQuoteString().trim());
        return quote[0];
    }

    private void withConnection(JdbcConnectionConsumer consumer) {
        this.connectionProvider.withConnection(consumer);
    }
}

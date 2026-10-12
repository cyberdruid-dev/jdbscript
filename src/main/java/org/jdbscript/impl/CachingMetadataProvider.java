package org.jdbscript.impl;

import org.jdbscript.DBMSType;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.IJDBCache.IJDBCacheKey;
import org.jdbscript.impl.cache.NoCache;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CachingMetadataProvider extends AbstractMetadataProviderProxy {

    private record DBMSTypeKey() implements IJDBCacheKey<DBMSType> {}
    private record AllTablesKey() implements IJDBCacheKey<List<String>> {}
    private record SortedTablesKey() implements IJDBCacheKey<List<String>> {}
    private record TableDependencyKey(String tableName) implements IJDBCacheKey<Set<String>> {}
    private record PrimaryKeyKey(String dbTableName) implements IJDBCacheKey<List<String>> {}
    private record ColumnsKey(String dbTableName) implements IJDBCacheKey<List<String>> {}
    private record LobColumnsKey(String dbTableName) implements IJDBCacheKey<Map<String, Integer>> {}
    private record ColumnTypesKey(String dbTableName) implements IJDBCacheKey<Map<String, String>> {}
    private record IdentityColumnKey(String dbTableName) implements IJDBCacheKey<Optional<String>> {}
    private record IdentifierQuoteKey() implements IJDBCacheKey<String> {}
    private record DbTableNameKey(String tableName) implements IJDBCacheKey<String> {}
    private record DbColumnNameKey(String tableName, String columnName) implements IJDBCacheKey<String> {}
    private static final DBMSTypeKey DBMS_TYPE_KEY = new DBMSTypeKey();
    private static final AllTablesKey ALL_TABLES_KEY = new AllTablesKey();
    private static final SortedTablesKey SORTED_TABLES_KEY = new SortedTablesKey();
    private static final IdentifierQuoteKey IDENTIFIER_QUOTE_KEY = new IdentifierQuoteKey();

    private volatile IJDBCache cache = new NoCache();
    // Also kept outside the cache: with NoCache, every call would open a connection.
    private volatile DBMSType dbmsType;

    public CachingMetadataProvider(IMetadataProvider delegate) {
        super(delegate);
    }

    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
    }

    @Override
    public DBMSType getDbmsType() {
        if (dbmsType == null) {
            dbmsType = cache.getOrCompute(DBMS_TYPE_KEY, k -> super.getDbmsType());
        }
        return dbmsType;
    }

    @Override
    public List<String> getAllTables() {
        return cache.getOrCompute(ALL_TABLES_KEY, k -> super.getAllTables());
    }

    // Cached per table, but the first miss fetches it and all remaining tables in one batch, i.e. over one connection.
    @Override
    public Map<String, Set<String>> getTableDependencies(Collection<String> tableNames) {
        List<String> names = List.copyOf(tableNames);
        Map<String, Set<String>> fetched = new HashMap<>();
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            List<String> remaining = names.subList(i, names.size());
            result.put(name, cache.getOrCompute(new TableDependencyKey(name), k -> {
                if (!fetched.containsKey(name)) {
                    fetched.putAll(super.getTableDependencies(remaining));
                }
                return fetched.get(name);
            }));
        }
        return result;
    }

    @Override
    public List<String> getPrimaryKeyColumns(String dbTableName) {
        return cache.getOrCompute(new PrimaryKeyKey(dbTableName), k -> super.getPrimaryKeyColumns(dbTableName));
    }

    @Override
    public List<String> getColumns(String dbTableName) {
        return cache.getOrCompute(new ColumnsKey(dbTableName), k -> super.getColumns(dbTableName));
    }

    @Override
    public Map<String, Integer> getLobColumns(String dbTableName) {
        return cache.getOrCompute(new LobColumnsKey(dbTableName), k -> super.getLobColumns(dbTableName));
    }

    @Override
    public Map<String, String> getColumnTypes(String dbTableName) {
        return cache.getOrCompute(new ColumnTypesKey(dbTableName), k -> super.getColumnTypes(dbTableName));
    }

    @Override
    public Optional<String> getIdentityColumn(String dbTableName) {
        return cache.getOrCompute(new IdentityColumnKey(dbTableName), k -> super.getIdentityColumn(dbTableName));
    }

    @Override
    public String getIdentifierQuote() {
        return cache.getOrCompute(IDENTIFIER_QUOTE_KEY, k -> super.getIdentifierQuote());
    }

    @Override
    public String toDbTableName(String tableName) {
        return cache.getOrCompute(new DbTableNameKey(tableName), k -> super.toDbTableName(tableName));
    }

    @Override
    public String toDbColumnName(String tableName, String columnName) {
        return cache.getOrCompute(new DbColumnNameKey(tableName, columnName), k -> super.toDbColumnName(tableName, columnName));
    }

    @Override
    public List<String> getSortedTables() {
        return cache.getOrCompute(SORTED_TABLES_KEY, k -> super.getSortedTables());
    }
}

package org.jdbscript.impl.sql;

import org.jdbscript.DbmsType;
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.NoCache;
import org.jdbscript.impl.cache.TableDependenciesKey;
import org.jdbscript.impl.sql.SqlConnectionProvider.JdbcConnectionConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static java.util.Collections.emptyList;

public class SqlMetadataProvider implements IMetadataProvider {
    private static final Logger log = LoggerFactory.getLogger(SqlMetadataProvider.class);

    private final SqlConnectionProvider connectionProvider;
    private IJDBCache cache = new NoCache();
    private DbmsType dbmsType;

    private List<String> allTables;
    private List<String> globalSortedTables;

    public SqlMetadataProvider(SqlConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
    }

    @Override
    public DbmsType getDbmsType() {
        if (dbmsType == null) {
            withConnection(cnn -> {
                dbmsType = DbmsType.getTypeFromUrl(cnn.getMetaData().getURL());
            });
        }
        return dbmsType;
    }

    @Override
    public List<String> getAllTables() {
        ensureInitialized();
        return allTables;
    }

    @Override
    public List<String> getSortedTables() {
        ensureInitialized();
        return globalSortedTables;
    }

    @Override
    public Comparator<String> getParentChildTableComparator() {
        ensureInitialized();
        List<String> sorted = getSortedTables();
        return (t1, t2) -> {
            int i1 = findIndex(sorted, t1);
            int i2 = findIndex(sorted, t2);
            if (i1 == -1 || i2 == -1) {
                return t1.compareToIgnoreCase(t2);
            }
            return Integer.compare(i1, i2);
        };
    }

    private int findIndex(List<String> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private void ensureInitialized() {
        if (allTables == null) {
            withConnection(cnn -> {
                DatabaseMetaData metaData = cnn.getMetaData();
                String catalog = cnn.getCatalog();
                String schema = cnn.getSchema();

                List<String> tables = new ArrayList<>();
                try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_NAME"));
                    }
                }
                allTables = tables;
                globalSortedTables = sortTablesByDependencies(allTables);
            });
        }
    }

    @Override
    public List<String> sortTablesByDependencies(Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return emptyList();
        }

        List<String> namesList = new ArrayList<>(tableNames);
        Map<String, String> normalizedNames = new HashMap<>();
        for (String name : namesList) {
            normalizedNames.put(name.toUpperCase(), name);
        }

        Map<String, Set<String>> dependencies = new HashMap<>();
        withConnection(cnn -> {
            DatabaseMetaData metaData = cnn.getMetaData();
            String catalog = cnn.getCatalog();
            String schema = cnn.getSchema();
            for (String tableName : namesList) {
                Set<String> rawDeps;
                try {
                    rawDeps = cache.getOrCompute(new TableDependenciesKey(tableName), k -> {
                        try {
                            return getRawTableDependencies(metaData, catalog, schema, k.tableName());
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (RuntimeException e) {
                    if (e.getCause() instanceof SQLException) {
                        throw (SQLException) e.getCause();
                    }
                    throw e;
                }

                Set<String> tableDeps = new HashSet<>();
                for (String rawDep : rawDeps) {
                    String normalizedParent = normalizedNames.get(rawDep);
                    if (normalizedParent != null && !normalizedParent.equalsIgnoreCase(tableName)) {
                        tableDeps.add(normalizedParent);
                    }
                }
                dependencies.put(tableName, tableDeps);
            }
        });

        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String table : namesList) {
            sortRecursive(table, dependencies, visited, visiting, sorted);
        }

        return sorted;
    }

    private Set<String> getRawTableDependencies(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        Set<String> allDeps = new HashSet<>();
        collectImportedKeys(allDeps, metaData, catalog, schema, tableName);

        // If nothing found, try upper case
        if (allDeps.isEmpty()) {
            String upperTableName = tableName.toUpperCase();
            if (!upperTableName.equals(tableName)) {
                collectImportedKeys(allDeps, metaData, catalog, schema, upperTableName);
            }
        }

        // For some DBs (like Postgres or Oracle) catalog/schema handling in getImportedKeys can be tricky.
        // If still nothing found, try with null catalog and schema to be more permissive.
        if (allDeps.isEmpty() && (catalog != null || schema != null)) {
            collectImportedKeys(allDeps, metaData, null, null, tableName);
            if (allDeps.isEmpty()) {
                String upperTableName = tableName.toUpperCase();
                if (!upperTableName.equals(tableName)) {
                    collectImportedKeys(allDeps, metaData, null, null, upperTableName);
                }
            }
        }

        return allDeps;
    }

    private void collectImportedKeys(Set<String> allDeps, DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String pkTableName = rs.getString("PKTABLE_NAME");
                if (pkTableName != null) {
                    allDeps.add(pkTableName.toUpperCase());
                }
            }
        } catch (SQLException e) {
            // Some drivers might throw exception instead of returning empty result set if table not found or parameters are wrong
            log.debug("Failed to get imported keys for table {}: {}", tableName, e.getMessage());
        }
    }

    private void sortRecursive(String table, Map<String, Set<String>> dependencies,
                               Set<String> visited, Set<String> visiting, List<String> sorted) {
        if (visited.contains(table)) return;
        if (visiting.contains(table)) {
            throw new IllegalStateException("Circular dependency detected involving table: " + table);
        }

        visiting.add(table);
        Set<String> tableDeps = dependencies.getOrDefault(table, Collections.emptySet());
        for (String dep : tableDeps) {
            sortRecursive(dep, dependencies, visited, visiting, sorted);
        }
        visiting.remove(table);
        visited.add(table);
        sorted.add(table);
    }



    private void withConnection(JdbcConnectionConsumer<Connection> consumer) {
        this.connectionProvider.withConnection(consumer);
    }
}

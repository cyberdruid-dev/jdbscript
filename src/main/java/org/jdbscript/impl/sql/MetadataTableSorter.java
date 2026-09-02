package org.jdbscript.impl.sql;

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

public class MetadataTableSorter implements ITableSorter{
    private static final Logger log = LoggerFactory.getLogger(MetadataTableSorter.class);

    private final SqlConnectionProvider connectionProvider;
    private IJDBCache cache = new NoCache();

    public MetadataTableSorter(SqlConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
    }

    @Override
    public List<String> sortTablesByDependencies(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return emptyList();
        }

        Map<String, String> normalizedNames = new HashMap<>();
        for (String name : tableNames) {
            normalizedNames.put(name.toUpperCase(), name);
        }

        Map<String, Set<String>> dependencies = new HashMap<>();
        withConnection(cnn -> {
            DatabaseMetaData metaData = cnn.getMetaData();
            String catalog = cnn.getCatalog();
            String schema = cnn.getSchema();
            for (String tableName : tableNames) {
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

        for (String table : tableNames) {
            sortRecursive(table, dependencies, visited, visiting, sorted);
        }

        return sorted;
    }

    private Set<String> getRawTableDependencies(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        Set<String> allDeps = new HashSet<>();
        // Try exact name
        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                allDeps.add(rs.getString("PKTABLE_NAME").toUpperCase());
            }
        }
        // If nothing found, try upper case
        String upperTableName = tableName.toUpperCase();
        if (allDeps.isEmpty() && !upperTableName.equals(tableName)) {
            try (ResultSet rs = metaData.getImportedKeys(catalog, schema, upperTableName)) {
                while (rs.next()) {
                    allDeps.add(rs.getString("PKTABLE_NAME").toUpperCase());
                }
            }
        }
        return allDeps;
    }

    private void sortRecursive(String table, Map<String, Set<String>> dependencies,
                               Set<String> visited, Set<String> visiting, List<String> sorted) {
        if (visited.contains(table)) return;
        if (visiting.contains(table)) {
            log.warn("Circular dependency detected involving table: {}", table);
            return;
        }

        visiting.add(table);
        Set<String> tableDeps = dependencies.getOrDefault(table, Collections.emptySet());
        for (String dep : tableDeps) {
            sortRecursive(dep, dependencies, visited, visiting, sorted);
        }
        visiting.remove(table);
        visited.add(table);
        sorted.add(0, table);
    }



    private void withConnection(JdbcConnectionConsumer<Connection> consumer) {
        this.connectionProvider.withConnection(consumer);
    }
}

package org.jdbscript.impl.sql;

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

    public MetadataTableSorter(SqlConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
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
                Set<String> tableDeps = new HashSet<>();
                findImportedKeys(metaData, catalog, schema, tableName, tableDeps, normalizedNames);
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

    private void findImportedKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName,
                                   Set<String> tableDeps, Map<String, String> normalizedNames) throws SQLException {
        // Try exact name
        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName)) {
            fillDeps(rs, tableDeps, normalizedNames, tableName);
        }
        // If nothing found, try upper case
        if (tableDeps.isEmpty()) {
            try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName.toUpperCase())) {
                fillDeps(rs, tableDeps, normalizedNames, tableName);
            }
        }
    }

    private void fillDeps(ResultSet rs, Set<String> tableDeps, Map<String, String> normalizedNames, String tableName) throws SQLException {
        while (rs.next()) {
            String parentTable = rs.getString("PKTABLE_NAME");
            String normalizedParent = normalizedNames.get(parentTable.toUpperCase());
            if (normalizedParent != null && !normalizedParent.equalsIgnoreCase(tableName)) {
                tableDeps.add(normalizedParent);
            }
        }
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

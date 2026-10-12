package org.jdbscript.impl;

import org.jdbscript.DBMSType;
import org.jdbscript.errors.JDBErrors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Provides metadata about the database tables and their dependencies.
 * <p>
 * Implementations fetch the abstract methods' data; the default methods derive everything else
 * from them, so a caching proxy that overrides only the fetches also covers the derived lookups.
 * Methods taking a {@code dbTableName} expect the DBMS's own spelling, see {@link #toDbTableName}.
 */
public interface IMetadataProvider {
    /**
     * Returns the detected DBMS type.
     * @return the DBMS type
     */
    DBMSType getDbmsType();

    /**
     * Returns a list of all table names discovered in the database.
     * @return list of table names
     */
    List<String> getAllTables();

    /** @return upper-cased names of the tables {@code tableName} references through foreign keys */
    Set<String> getTableDependencies(String tableName);

    List<String> getPrimaryKeyColumns(String dbTableName);

    List<String> getColumns(String dbTableName);

    /**
     * CLOB/NCLOB/BLOB columns, which a DBMS like Oracle can't compare with {@code =}.
     * @return upper-cased column name to {@link java.sql.Types} constant
     */
    Map<String, Integer> getLobColumns(String dbTableName);

    /** @return lower-cased column name to the DBMS's own type name */
    Map<String, String> getColumnTypes(String dbTableName);

    Optional<String> getIdentityColumn(String dbTableName);

    /** @return empty if the DBMS doesn't support quoting identifiers */
    String getIdentifierQuote();

    /**
     * Metadata lookups and quoted identifiers need the DBMS's own spelling of a table name.
     * @param tableName e.g. a schema interface method name
     * @return the matching name from {@link #getAllTables()}, or {@code tableName} if none matches
     */
    default String toDbTableName(String tableName) {
        return Utils.resolveIdentifier(tableName, getAllTables(),
                matches -> JDBErrors.AMBIGUOUS_TABLE_NAME.get(tableName, String.join(", ", matches)));
    }

    default String toDbColumnName(String tableName, String columnName) {
        String dbTableName = toDbTableName(tableName);
        return Utils.resolveIdentifier(columnName, getColumns(dbTableName),
                matches -> JDBErrors.AMBIGUOUS_COLUMN_NAME.get(columnName, dbTableName, String.join(", ", matches)));
    }

    default String quoteTableName(String tableName) {
        return getIdentifierQuote() + toDbTableName(tableName) + getIdentifierQuote();
    }

    default String quoteColumnName(String tableName, String columnName) {
        return getIdentifierQuote() + toDbColumnName(tableName, columnName) + getIdentifierQuote();
    }

    /**
     * Returns all discovered tables sorted by their dependencies (parents before children).
     * @return sorted list of table names
     */
    default List<String> getSortedTables() {
        return sortTablesByDependencies(getAllTables());
    }

    /**
     * Returns a comparator that orders tables based on their global dependencies.
     * Parent tables will be ordered before child tables.
     * @return a comparator for table names
     */
    default Comparator<String> getParentChildTableComparator() {
        List<String> sorted = getSortedTables();
        return (t1, t2) -> {
            int i1 = indexOfIgnoreCase(sorted, t1);
            int i2 = indexOfIgnoreCase(sorted, t2);
            if (i1 == -1 || i2 == -1) {
                return t1.compareToIgnoreCase(t2);
            }
            return Integer.compare(i1, i2);
        };
    }

    /**
     * Sorts the provided collection of table names based on their dependencies.
     * @param tableNames the tables to sort
     * @return sorted list of the provided table names
     */
    default List<String> sortTablesByDependencies(Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return List.of();
        }
        List<String> namesList = new ArrayList<>(tableNames);
        Map<String, String> normalizedNames = new HashMap<>();
        for (String name : namesList) {
            normalizedNames.put(name.toUpperCase(), name);
        }

        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String tableName : namesList) {
            Set<String> tableDeps = new HashSet<>();
            for (String rawDep : getTableDependencies(tableName)) {
                String normalizedParent = normalizedNames.get(rawDep);
                if (normalizedParent != null && !normalizedParent.equalsIgnoreCase(tableName)) {
                    tableDeps.add(normalizedParent);
                }
            }
            dependencies.put(tableName, tableDeps);
        }

        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String table : namesList) {
            sortRecursive(table, dependencies, visited, visiting, sorted);
        }
        return sorted;
    }

    private static void sortRecursive(String table, Map<String, Set<String>> dependencies,
                                      Set<String> visited, Set<String> visiting, List<String> sorted) {
        if (visited.contains(table)) return;
        if (visiting.contains(table)) {
            throw new IllegalStateException("Circular dependency detected involving table: " + table);
        }

        visiting.add(table);
        for (String dep : dependencies.getOrDefault(table, Collections.emptySet())) {
            sortRecursive(dep, dependencies, visited, visiting, sorted);
        }
        visiting.remove(table);
        visited.add(table);
        sorted.add(table);
    }

    private static int indexOfIgnoreCase(List<String> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}

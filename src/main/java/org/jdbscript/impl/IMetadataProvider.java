package org.jdbscript.impl;
 
import org.jdbscript.DBMSType;
import org.jdbscript.errors.JDBErrors;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
 
/**
 * Provides metadata about the database tables and their dependencies.
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

    /**
     * Returns the DBMS's own spelling of a table name: an exact match if there is one, otherwise a
     * the only case-insensitive one. Metadata lookups and quoted identifiers need the exact spelling.
     * @param tableName the table name, e.g. a schema interface method name
     * @return the matching table name from {@link #getAllTables()}, or {@code tableName} if none matches
     */
    default String toDbTableName(String tableName) {
        List<String> allTables = getAllTables();
        if (allTables.contains(tableName)) {
            return tableName;
        }
        List<String> matches = allTables.stream()
                .filter(t -> t.equalsIgnoreCase(tableName))
                .toList();
        if (matches.size() > 1) {
            throw JDBErrors.AMBIGUOUS_TABLE_NAME.get(tableName, String.join(", ", matches));
        }
        return matches.isEmpty() ? tableName : matches.get(0);
    }

    /**
     * Returns all discovered tables sorted by their dependencies (parents before children).
     * @return sorted list of table names
     */
    List<String> getSortedTables();

    /**
     * Returns a comparator that orders tables based on their global dependencies.
     * Parent tables will be ordered before child tables.
     * @return a comparator for table names
     */
    Comparator<String> getParentChildTableComparator();

    /**
     * Sorts the provided collection of table names based on their dependencies.
     * @param tableNames the tables to sort
     * @return sorted list of the provided table names
     */
    List<String> sortTablesByDependencies(Collection<String> tableNames);
}

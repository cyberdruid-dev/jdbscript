package org.jdbscript.impl;
 
import org.jdbscript.DbmsType;

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
    DbmsType getDbmsType();

    /**
     * Returns a list of all table names discovered in the database.
     * @return list of table names
     */
    List<String> getAllTables();

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

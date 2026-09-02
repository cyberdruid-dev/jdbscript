package org.jdbscript;

import org.jdbscript.impl.JDbScript;
import org.jdbscript.impl.cache.IJDBCache;

import javax.sql.DataSource;
import java.util.List;

/**
 * Strategy interface responsible for executing database insert operations and table cleanups.
 */
public interface IScriptExecutor {

    /**
     * Sets the data source to be used for database connections.
     *
     * @param dataSource the JDBC data source
     */
    void setDataSource(DataSource dataSource);

    /**
     * Sets the target DBMS type.
     *
     * @param dbmsType the detected or configured DBMS type
     */
    void setDbmsType(DbmsType dbmsType);

    /**
     * Inserts all records contained within the compiled script into the target database.
     *
     * @param dbScript the compiled script containing records to insert
     */
    void insert(JDbScript dbScript);

    /**
     * Cleans up (deletes all records from) the specified tables.
     *
     * @param tableNames the names of the tables to truncate or delete
     */
    void cleanupTables(List<String> tableNames);

    /**
     * Sorts the specified tables based on their Foreign Key dependencies to ensure
     * that child tables appear before their parent tables in the list (suitable for cleanup).
     *
     * @param tableNames the names of the tables to sort
     * @return a new list containing the tables in a valid cleanup order
     */
    List<String> sortTablesByDependencies(List<String> tableNames);

    void assertRowsExist(JDbScript script);

    void assertRowsNotExist(JDbScript script);

    /**
     * Sets the cache to be used for metadata calculations.
     *
     * @param cache the metadata cache
     */
    void setCache(IJDBCache cache);
}

package org.jdbscript;
 
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.impl.JDBScript;
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
     * Inserts all records contained within the compiled script into the target database.
     *
     * @param dbScript the compiled script containing records to insert
     */
    void insert(JDBScript dbScript);

    /**
     * Cleans up (deletes all records from) the specified tables.
     *
     * @param tableNames the names of the tables to truncate or delete
     */
    void cleanupTables(List<String> tableNames);

    IMetadataProvider getMetadataProvider();

    void assertRowsExist(JDBScript script);

    void assertRowsNotExist(JDBScript script);

    /**
     * Sets the cache to be used for metadata calculations.
     *
     * @param cache the metadata cache
     */
    void setCache(IJDBCache cache);

    /**
     * Sets the enabled features. Implementations that don't have any DBMS-specific behavior gated
     * by a feature can ignore this.
     *
     * @param features the enabled features
     */
    void setFeatures(JDBFeatureSet features);
}

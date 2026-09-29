package org.jdbscript;

import java.util.function.Consumer;

/**
 * Main engine interface for executing JDBScript database seeding, population, and cleanup operations.
 *
 * @param <T> the schema interface type extending {@link IDBSchema}
 */
public interface IJDBEngine <T extends IDBSchema>{

    /**
     * Cleans up all tables declared in the schema and executes the specified class-based script.
     *
     * @param scriptClass the class extending the schema interface that defines dataset fixtures
     */
    void resetDB(Class<? extends T> scriptClass);

    /**
     * Inserts records defined in the specified class-based script without cleaning up existing table data.
     *
     * @param scriptClass the class extending the schema interface that defines dataset fixtures
     */
    void insertDB(Class<? extends T> scriptClass);

    /**
     * Cleans up all tables declared in the schema and executes the specified inline lambda script.
     *
     * @param db a {@link Consumer} receiving the schema proxy to define and insert records
     */
    void resetDB(Consumer<T> db);

    /**
     * Inserts records defined in the specified inline lambda script without cleaning up existing table data.
     *
     * @param db a {@link Consumer} receiving the schema proxy to define and insert records
     */
    void insertDB(Consumer<T> db);

    /**
     * Deletes all records from the tables defined in the schema in the reverse order of declaration.
     */
    void cleanupDB();

    /**
     * Updates existing rows: for each record, its primary key columns (read from the database
     * metadata) select the row, and every other column set in the script is updated. No defaults
     * are applied - only the columns set in the script are touched.
     *
     * @param db a {@link Consumer} receiving the schema proxy to define the rows to update
     */
    void updateDB(Consumer<T> db);

    /**
     * Updates existing rows from the specified class-based script, as {@link #updateDB(Consumer)} does.
     *
     * @param scriptClass the class extending the schema interface that defines the rows to update
     */
    void updateDB(Class<? extends T> scriptClass);

    void assertDBHas(Consumer<T> dbAsserts);

    void assertDBHasNot(Consumer<T> dbAsserts);

}

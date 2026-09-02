package org.jdbscript;

import java.util.function.Consumer;

/**
 * Main engine interface for executing JDBScript database seeding, population, and cleanup operations.
 *
 * @param <T> the schema interface type extending {@link IDbSchema}
 */
public interface IJDBEngine <T extends IDbSchema>{

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

    void assertDBHas(Consumer<T> dbAsserts);

    void assertDBHasNot(Consumer<T> dbAsserts);

}

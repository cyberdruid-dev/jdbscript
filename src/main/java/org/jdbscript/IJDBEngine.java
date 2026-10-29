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

    /**
     * Returns a view of this engine whose scripts receive an {@code S} proxy, so lambda scripts can
     * call {@code S}'s default helper methods. Everything else - id counters, caches, callbacks,
     * cleanup and schema validation against {@code T} - is shared with this engine.
     *
     * @param schemaClass a sub-interface of this engine's schema adding default helper methods
     * @param <S> the sub-interface type
     * @return a view of this engine for {@code S}
     */
    <S extends T> IJDBEngine<S> as(Class<S> schemaClass);

    /**
     * Asserts that every record in the script matches at least one row of its table. Records are
     * checked independently, so several records may match the same row.
     * <ul>
     *   <li>Only the columns set on a record are compared; the others are ignored.</li>
     *   <li>{@code null} matches SQL NULL.</li>
     *   <li>Values go through the same converters as {@link #insertDB(Consumer)}, but
     *       {@code defaults} are not applied.</li>
     *   <li>The database compares the values; LOB columns are compared in full.</li>
     *   <li>A record with no columns set is an error.</li>
     * </ul>
     * On failure, the {@link org.opentest4j.AssertionFailedError} lists every record without a
     * matching row.
     *
     * @param dbAsserts a {@link Consumer} receiving the schema proxy to define the expected records
     */
    void assertDBHas(Consumer<T> dbAsserts);

    /**
     * Asserts that no record in the script matches any row of its table. Matching follows the
     * same rules as {@link #assertDBHas(Consumer)}. On failure, the
     * {@link org.opentest4j.AssertionFailedError} lists every matching record with one of its
     * rows.
     *
     * @param dbAsserts a {@link Consumer} receiving the schema proxy to define the records that must not exist
     */
    void assertDBHasNot(Consumer<T> dbAsserts);

}

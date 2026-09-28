package org.jdbscript;

import java.util.function.Consumer;

/**
 * Base interface representing a database schema definition.
 * <p>
 * User schema interfaces extend this interface and declare methods representing database tables,
 * where each method returns an {@link IDBRecord} sub-interface modeling the columns of that table.
 * </p>
 * <p>
 * Example:
 * <pre>{@code
 * public interface IAppSchema extends IDBSchema {
 *     IUserRecord users();
 *     IOrderRecord orders();
 *
 *     interface IUserRecord extends IDBRecord {
 *         IUserRecord id(Long id);
 *         IUserRecord username(String username);
 *         IUserRecord email(String email);
 *     }
 * }
 * }</pre>
 */
public interface IDBSchema {

    /**
     * Includes and executes an inline or lambda-based script within the current schema execution context.
     *
     * @param script a {@link Consumer} accepting the schema instance to populate records
     */
    void include(Consumer<? extends IDBSchema> script);

    /**
     * Includes and executes a class-based reusable script within the current schema execution context.
     *
     * @param script the class representing the reusable dataset/fixture to execute
     */
    void include(Class<? extends IDBSchema> script);

    /**
     * Marker interface for database table record representations.
     * <p>
     * Interfaces extending {@code IDBRecord} define fluent builder methods for column values.
     * Each method's name is used verbatim as the SQL column name - {@code user_id(Long id)} sets
     * the {@code user_id} column, not {@code userId}. There's no camelCase-to-snake_case
     * conversion or annotation to override it: name your methods exactly as the column is named
     * in the database. A record is backed by a dynamic proxy, so the name is read from the
     * invoked {@link java.lang.reflect.Method} at call time and inserted unquoted into the
     * generated SQL - so an exact match still follows your DBMS's own case-folding rules for
     * unquoted identifiers (e.g. Postgres lowercases, Oracle uppercases).
     * </p>
     * <p>
     * Default methods can be defined to set default column values using {@link RecordTools}.
     * </p>
     */
    interface IDBRecord {

    }
}

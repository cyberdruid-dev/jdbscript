package org.jdbscript.impl.sql;

/**
 * Like {@link java.util.function.Consumer}, but allowed to throw a checked exception.
 *
 * @param <T> the input type
 */
@FunctionalInterface
interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
}

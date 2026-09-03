package org.jdbscript.impl.cache;

import java.util.function.Function;

public interface IJDBCache {
    /**
     * marker interface that adds a bit of type safety. e.i. binds value's type to the key.
      * @param <V>
     */
    interface IJDBCacheKey<V> { }
    /**
     * Returns the cached value for the given key, or computes and stores it
     * using the mapping function if absent.
     * <p>
     * Implementations must support {@code computeFunction} itself calling {@code getOrCompute}
     * again on this same cache (e.g. resolving one cached value while computing another), from the
     * same thread. A naive implementation backed directly by {@link java.util.concurrent.ConcurrentHashMap#computeIfAbsent}
     * would throw {@code IllegalStateException: Recursive update} in that case.
     */
    <V, K extends IJDBCacheKey<V>> V getOrCompute(K key, Function<K, V> computeFunction);

    /**
     * Invalidates a specific key in the cache.
     */
    <K extends IJDBCacheKey<?>> void invalidate(K key);

    /**
     * Clears all entries from the cache.
     */
    void clear();
}

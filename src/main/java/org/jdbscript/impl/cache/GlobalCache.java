package org.jdbscript.impl.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class GlobalCache implements IJDBCache {
    private static final Map STORAGE = new ConcurrentHashMap<>();

    @Override
    public <V, K extends IJDBCacheKey<V>> V getOrCompute(K key, Function<K, V> computeFunction) {
        return (V) STORAGE.computeIfAbsent(key, computeFunction);
    }

    @Override
    public <K extends IJDBCacheKey<?>> void invalidate(K key) {
        STORAGE.remove(key);
    }

    @Override
    public void clear() {
        STORAGE.clear();
    }
}


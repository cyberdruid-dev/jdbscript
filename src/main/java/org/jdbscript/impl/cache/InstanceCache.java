package org.jdbscript.impl.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class InstanceCache implements IJDBCache {
    private final Map storage = new ConcurrentHashMap<>();

    @Override
    public <V, K extends IJDBCacheKey<V>> V getOrCompute(K key, Function<K, V> computeFunction) {
        return (V) storage.computeIfAbsent(key, computeFunction);
    }

    @Override
    public <K extends IJDBCacheKey<?>> void invalidate(K key) {
        storage.remove(key);
    }

    @Override
    public void clear() {
        storage.clear();
    }
}


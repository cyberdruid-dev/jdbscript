package org.jdbscript.impl.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class InstanceCache implements IJDBCache {
    private final Map storage = new HashMap<>();
    private final Map<Object, Object> keyLocks = new ConcurrentHashMap<>();
    private long clearCount;

    // Per-key lock rather than one for the whole cache: computing does blocking JDBC I/O. Plain
    // monitors rather than ConcurrentHashMap.computeIfAbsent, which throws "Recursive update" when
    // a compute function calls getOrCompute again.
    @Override
    public <V, K extends IJDBCacheKey<V>> V getOrCompute(K key, Function<K, V> computeFunction) {
        synchronized (this) {
            if (storage.containsKey(key)) {
                return (V) storage.get(key);
            }
        }
        synchronized (keyLocks.computeIfAbsent(key, k -> new Object())) {
            long clearCountBeforeCompute;
            synchronized (this) {
                if (storage.containsKey(key)) {
                    return (V) storage.get(key);
                }
                clearCountBeforeCompute = clearCount;
            }
            V computed = computeFunction.apply(key);
            storeUnlessClearedSince(key, computed, clearCountBeforeCompute);
            return computed;
        }
    }

    private synchronized void storeUnlessClearedSince(Object key, Object value, long clearCountBeforeCompute) {
        if (value != null && clearCount == clearCountBeforeCompute) {
            storage.put(key, value);
        }
    }

    @Override
    public synchronized <K extends IJDBCacheKey<?>> void invalidate(K key) {
        clearCount++;
        storage.remove(key);
    }

    @Override
    public synchronized void clear() {
        clearCount++;
        storage.clear();
    }
}
